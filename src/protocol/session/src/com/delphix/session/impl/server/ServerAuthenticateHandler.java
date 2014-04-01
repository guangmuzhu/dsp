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
import com.delphix.session.impl.frame.AuthenticateRequest;
import com.delphix.session.impl.frame.AuthenticateResponse;
import com.delphix.session.impl.frame.LoginStatus;
import org.jboss.netty.channel.*;

/**
 * Netty channel handler for the login authenticate phase.
 */
@org.jboss.netty.channel.ChannelHandler.Sharable
public class ServerAuthenticateHandler extends SessionTransportHandler {

    private static final String HANDLER_NAME = "authenticate";

    public ServerAuthenticateHandler(SessionTransportManager manager) {
        super(manager, HANDLER_NAME);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Channel channel = ctx.getChannel();

        /*
         * Verify the message is an authenticate request. Anything else during the authentication phase is a protocol
         * violation and shall cause the connection to be closed immediately without response.
         */
        AuthenticateRequest request;

        try {
            request = (AuthenticateRequest) e.getMessage();
        } catch (ClassCastException exc) {
            throw new ProtocolViolationException("invalid login request", exc);
        }

        ServerTransport xport = (ServerTransport) manager.locate(channel);
        AuthenticateResponse response;

        response = xport.authenticate(request);

        /*
         * If the authentication attempt has failed, there is no need to continue with the login process. The
         * connection should closed as soon as the connect response is sent.
         */
        if (response.getStatus() != LoginStatus.SUCCESS) {
            logger.errorf("%s: login failed due to %s", xport, response.getStatus().getDesc());

            ChannelFuture future = channel.write(response);
            future.addListener(ChannelFutureListener.CLOSE);

            return;
        }

        /*
         * Authentication is successful. The channel pipeline needs to be reconfigured to prepare for the next login
         * phase.
         */
        if (response.isComplete()) {
            ChannelPipeline pipeline = channel.getPipeline();

            SessionTransportHandler param = manager.getNegotiateHandler();
            pipeline.replace(this, param.getName(), param);
        }

        channel.write(response);
    }
}
