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
import com.delphix.session.impl.frame.AuthenticateResponse;
import com.delphix.session.ssl.TransportSecurityLevel;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.ssl.SslHandler;

/**
 * Netty channel handler for the login authenticate phase.
 */
@org.jboss.netty.channel.ChannelHandler.Sharable
public class ClientAuthenticateHandler extends SessionTransportHandler {

    private static final String HANDLER_NAME = "authenticate";

    public ClientAuthenticateHandler(SessionTransportManager manager) {
        super(manager, HANDLER_NAME);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Channel channel = ctx.getChannel();

        /*
         * Verify the message is an authenticate response. Anything else from the server in the authenticate stage
         * is a protocol violation and shall cause the connection to be closed immediately without response.
         */
        AuthenticateResponse response;

        try {
            response = (AuthenticateResponse) e.getMessage();
        } catch (ClassCastException exc) {
            throw new ProtocolViolationException("invalid login response", exc);
        }

        /*
         * Process the authenticate response. In case of login failure, the login process shall be aborted and the
         * connection closed immediately.
         */
        final ClientTransport xport = (ClientTransport) manager.locate(channel);
        xport.authenticate(response);

        // Continue with the authenticate phase if necessary
        if (!response.isComplete()) {
            return;
        }

        /*
         * SASL authentication is successful. The channel pipeline needs to be reconfigured to prepare for the next
         * and the last login phase.
         */
        ChannelPipeline pipeline = channel.getPipeline();

        SessionTransportHandler negotiate = manager.getNegotiateHandler();
        pipeline.replace(this, negotiate.getName(), negotiate);

        if (xport.getTlsLevel() != TransportSecurityLevel.AUTHENTICATION) {
            xport.negotiate();
            return;
        }

        /*
         * For TLS level of AUTHENTICATION, close the SSL session and detach the SSL handler before proceeding to the
         * next login phase. TLS session teardown follows the sequence outlined below.
         *
         *  - client sends close_notify and upon success notifies outbound close future
         *  - server receives close_notify and notifies its inbound close future
         *  - server removes ssl handler from pipeline and sends close_notify
         *  - client receives close_notify and notifies its inbound close future
         *
         * As a client, we only remove the SSL handler from the pipeline after we received the inbound close_notify.
         * By then, the server should have already removed the ssl handler from its pipeline and the session closing
         * handshake done (i.e. all closing messages sent and consumed) to leave with us a clean and clear transport
         * data stream again.
         */
        SslHandler ssl = (SslHandler) pipeline.get("tls");
        ChannelFuture future = ssl.close();

        future.addListener(new ChannelFutureListener() {

            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                Channel channel = future.getChannel();

                if (!future.isSuccess()) {
                    xport.close();
                    return;
                }

                startNegotiate(channel);
            }
        });
    }

    private void startNegotiate(Channel channel) {
        ChannelPipeline pipeline = channel.getPipeline();
        SslHandler ssl = (SslHandler) pipeline.get("tls");

        ChannelFuture future = ssl.getSSLEngineInboundCloseFuture();

        future.addListener(new ChannelFutureListener() {

            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                Channel channel = future.getChannel();
                ClientTransport xport = (ClientTransport) manager.locate(channel);

                if (!future.isSuccess()) {
                    xport.close();
                    return;
                }

                ChannelPipeline pipeline = channel.getPipeline();
                pipeline.remove("tls");

                xport.negotiate();
            }
        });
    }
}
