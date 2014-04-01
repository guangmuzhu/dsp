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
 * Copyright (c) 2013, 2014 by Delphix. All rights reserved.
 */

package com.delphix.session.impl.server;

import com.delphix.session.impl.common.ProtocolViolationException;
import com.delphix.session.impl.common.SessionTransportHandler;
import com.delphix.session.impl.common.SessionTransportManager;
import com.delphix.session.impl.frame.*;
import org.jboss.netty.channel.*;

/**
 * Netty channel handler for the login negotiate phase.
 */
@org.jboss.netty.channel.ChannelHandler.Sharable
public class ServerNegotiateHandler extends SessionTransportHandler {

    private static final String HANDLER_NAME = "negotiate";

    public ServerNegotiateHandler(SessionTransportManager manager) {
        super(manager, HANDLER_NAME);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Channel channel = ctx.getChannel();

        /*
         * Verify the message is a negotiate request. Anything else during the negotiation phase is a protocol
         * violation and shall cause the connection to be closed immediately without response.
         */
        NegotiateRequest request;

        try {
            request = (NegotiateRequest) e.getMessage();
        } catch (ClassCastException exc) {
            throw new ProtocolViolationException("invalid login request", exc);
        }

        ServerTransport xport = (ServerTransport) manager.locate(channel);
        NegotiateResponse response;

        response = xport.negotiate(request);

        /*
         * If the negotiation attempt has failed, there is no need to continue with the login process. The
         * connection should closed as soon as the connect response is sent.
         */
        if (response.getStatus() != LoginStatus.SUCCESS) {
            logger.errorf("%s: login failed due to %s", xport, response.getStatus().getDesc());

            ChannelFuture future = channel.write(response);
            future.addListener(ChannelFutureListener.CLOSE);

            return;
        }

        /*
         * Session negotiation is successful. The incoming traffic must be disabled on the channel before sending the
         * negotiate response to avoid race with the client.
         */
        channel.setReadable(false);

        /*
         * The negotiate response must be sent before the channel pipeline is reconfigured to match the state of the
         * channel pipeline on the client. This is the last frame sent over this transport in the "clear" without the
         * effect of any data codec's.
         */
        channel.write(response);

        /*
         * The channel pipeline must be reconfigured to prepare for the operate phase. A set of data codec's may have
         * been negotiated. They must be added to the channel pipeline before read is enabled on the channel.
         */
        ChannelPipeline pipeline = channel.getPipeline();

        SessionTransportHandler op = manager.getOperateHandler(xport);
        pipeline.replace(this, op.getName(), op);

        SessionFrameEncoder encoder = (SessionFrameEncoder) pipeline.get("encoder");
        SessionFrameDecoder decoder = (SessionFrameDecoder) pipeline.get("decoder");

        SessionFrameOptions options = new SessionFrameOptions(xport.getNexus().getOptions());

        encoder.setOptions(options);
        decoder.setOptions(options);

        // Finally start the transport for the server role only
        xport.start(false);
    }
}
