/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Copyright (c) 2013 by Delphix. All rights reserved.
 */

package com.delphix.session.impl.server;

import com.delphix.session.impl.common.ProtocolViolationException;
import com.delphix.session.impl.common.SessionTransportHandler;
import com.delphix.session.impl.common.SessionTransportManager;
import com.delphix.session.impl.frame.ConnectRequest;
import com.delphix.session.impl.frame.ConnectResponse;
import com.delphix.session.impl.frame.LoginStatus;
import com.delphix.session.ssl.TransportSecurityLevel;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.SocketChannelConfig;
import org.jboss.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLSession;

/**
 * Netty channel handler for the login connect phase.
 */
@org.jboss.netty.channel.ChannelHandler.Sharable
public class ServerConnectHandler extends SessionTransportHandler {

    private static final String HANDLER_NAME = "connect";

    private final ServerManagerImpl serverManager;

    public ServerConnectHandler(SessionTransportManager manager, ServerManagerImpl serverManager) {
        super(manager, HANDLER_NAME);

        this.serverManager = serverManager;
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        Channel channel = ctx.getChannel();

        ServerTransport xport = new ServerTransport(serverManager);
        xport.notifyOpened(channel);

        manager.attach(channel, xport);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        Channel channel = ctx.getChannel();

        ServerTransport xport = (ServerTransport) manager.locate(channel);
        SocketChannelConfig config = (SocketChannelConfig) channel.getConfig();

        logger.infof("%s: socket send buffer size: %s (default)", xport, config.getSendBufferSize());
        logger.infof("%s: socket receive buffer size: %s (default)", xport, config.getReceiveBufferSize());

        xport.notifyConnected();
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Channel channel = ctx.getChannel();

        /*
         * Verify the message is a connect request. Anything else following connection establishment is a protocol
         * violation and shall cause the connection to be closed immediately without response.
         */
        ConnectRequest request;

        try {
            request = (ConnectRequest) e.getMessage();
        } catch (ClassCastException exc) {
            throw new ProtocolViolationException("invalid login request", exc);
        }

        /*
         * Notify the transport of the incoming connect request and expect a connect response in return. In case of
         * exception thrown while processing the connect request, the connection shall be closed immediately.
         */
        final ServerTransport xport = (ServerTransport) manager.locate(channel);
        ConnectResponse response = xport.connect(request);

        /*
         * If the attempt to connect has failed, there is no need to continue with the login process. The connection
         * should closed as soon as the connect response is sent.
         */
        if (response.getStatus() != LoginStatus.SUCCESS) {
            logger.errorf("%s: login failed due to %s", xport, response.getStatus().getDesc());

            ChannelFuture future = channel.write(response);
            future.addListener(ChannelFutureListener.CLOSE);

            return;
        }

        /*
         * Initial connect is successful. The channel pipeline needs to be reconfigured to prepare for the next login
         * phase.
         */
        ChannelPipeline pipeline = channel.getPipeline();

        SessionTransportHandler sasl = manager.getAuthenticateHandler();
        pipeline.replace(this, sasl.getName(), sasl);

        /*
         * If TLS is to be negotiated, an SSL handler is created with the startTLS flag set to true before it is
         * added to the pipeline. It effectively allows the first outbound packet sent over the channel pipeline,
         * or the connect response in our case which is also the equivalent of a STARTTLS response in protocols
         * like LDAP and SMTP, to bypass SSL encryption.
         */
        TransportSecurityLevel tlsLevel = xport.getTlsLevel();

        if (tlsLevel != null) {
            final SslHandler ssl = new SslHandler(xport.getServer().createSslEngine(), true);
            pipeline.addFirst("tls", ssl);

            // For TLS level of AUTHENTICATION, remove the SSL handler from the pipeline upon SSL session closure
            if (tlsLevel == TransportSecurityLevel.AUTHENTICATION) {
                ChannelFuture future = ssl.getSSLEngineInboundCloseFuture();

                future.addListener(new ChannelFutureListener() {

                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        Channel channel = future.getChannel();

                        if (!future.isSuccess()) {
                            xport.close();
                            return;
                        }

                        SSLSession session = ssl.getEngine().getSession();

                        logger.infof("%s: ssl negotiated protocol %s", xport, session.getProtocol());
                        logger.infof("%s: ssl negotiated cipher suite %s", xport, session.getCipherSuite());

                        ChannelPipeline pipeline = channel.getPipeline();
                        pipeline.remove("tls");
                    }
                });
            }
        }

        channel.write(response);
    }
}
