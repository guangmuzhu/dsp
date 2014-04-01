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

package com.delphix.session.impl.client;

import com.delphix.session.impl.common.ProtocolViolationException;
import com.delphix.session.impl.common.SessionTransportHandler;
import com.delphix.session.impl.common.SessionTransportManager;
import com.delphix.session.impl.frame.ConnectResponse;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.SocketChannelConfig;
import org.jboss.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLSession;

/**
 * Netty channel handler for the login connect phase.
 */
public class ClientConnectHandler extends SessionTransportHandler {

    private static final String HANDLER_NAME = "connect";

    private final ClientTransport xport;

    public ClientConnectHandler(SessionTransportManager manager, ClientTransport xport) {
        super(manager, HANDLER_NAME);
        this.xport = xport;
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        Channel channel = ctx.getChannel();

        manager.attach(channel, xport);
        xport.notifyOpened(channel);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        Channel channel = ctx.getChannel();

        SocketChannelConfig config = (SocketChannelConfig) channel.getConfig();

        logger.infof("%s: socket send buffer size: %s", xport, config.getSendBufferSize());
        logger.infof("%s: socket receive buffer size: %s", xport, config.getReceiveBufferSize());

        xport.notifyConnected();
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Channel channel = ctx.getChannel();

        /*
         * Verify the message is a connect response. Anything else following the initial connect request is a protocol
         * violation and shall cause the connection to be closed immediately without response.
         */
        ConnectResponse response;

        try {
            response = (ConnectResponse) e.getMessage();
        } catch (ClassCastException exc) {
            throw new ProtocolViolationException("invalid login response", exc);
        }

        /*
         * Process the connect response. In case of login failure, the login process shall be aborted and the
         * connection closed immediately.
         */
        xport.connect(response);

        /*
         * Initial connect is successful. The channel pipeline needs to be reconfigured to prepare for the next login
         * phase which, depending on the TLS setting, could be either TLS/SSL encrypt or SASL authenticate.
         */
        ChannelPipeline pipeline = channel.getPipeline();

        SessionTransportHandler sasl = manager.getAuthenticateHandler();
        pipeline.replace(this, sasl.getName(), sasl);

        // Initiate authenticate phase right away if TLS is not enabled
        if (xport.getTlsLevel() == null) {
            xport.authenticate();
            return;
        }

        final SslHandler ssl = new SslHandler(xport.getSession().createSslEngine());
        pipeline.addFirst("tls", ssl);

        ChannelFuture future = ssl.handshake();

        future.addListener(new ChannelFutureListener() {

            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (!future.isSuccess()) {
                    xport.close();
                    return;
                }

                SSLSession session = ssl.getEngine().getSession();

                logger.infof("%s: ssl negotiated protocol %s", xport, session.getProtocol());
                logger.infof("%s: ssl negotiated cipher suite %s", xport, session.getCipherSuite());

                xport.authenticate();
            }
        });
    }
}
