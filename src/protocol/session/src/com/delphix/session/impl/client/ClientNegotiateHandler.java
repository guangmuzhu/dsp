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

package com.delphix.session.impl.client;

import com.delphix.session.impl.common.ProtocolViolationException;
import com.delphix.session.impl.common.SessionTransportHandler;
import com.delphix.session.impl.common.SessionTransportManager;
import com.delphix.session.impl.frame.NegotiateResponse;
import com.delphix.session.impl.frame.SessionFrameDecoder;
import com.delphix.session.impl.frame.SessionFrameEncoder;
import com.delphix.session.impl.frame.SessionFrameOptions;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.MessageEvent;

/**
 * Netty channel handler for the login negotiate phase.
 */
@org.jboss.netty.channel.ChannelHandler.Sharable
public class ClientNegotiateHandler extends SessionTransportHandler {

    private static final String HANDLER_NAME = "negotiate";

    public ClientNegotiateHandler(SessionTransportManager manager) {
        super(manager, HANDLER_NAME);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Channel channel = ctx.getChannel();

        /*
         * Verify the message is a negotiate response. Anything else from the server in the negotiate stage is
         * a protocol violation and shall cause the connection to be closed immediately without response.
         */
        NegotiateResponse response;

        try {
            response = (NegotiateResponse) e.getMessage();
        } catch (ClassCastException exc) {
            throw new ProtocolViolationException("invalid login response", exc);
        }

        /*
         * Process the negotiate response. In case of login failure, the login process shall be aborted and the
         * connection closed immediately.
         */
        ClientTransport xport = (ClientTransport) manager.locate(channel);
        xport.negotiate(response);

        /*
         * Session negotiation is successful. The channel pipeline must be reconfigured to prepare for the operate
         * phase. A set of data codec's may have been negotiated. Furthermore, they have been added to the channel
         * pipeline on the server before negotiate response is sent. We must do the same on the client now before
         * the next frame is sent.
         */
        ChannelPipeline pipeline = channel.getPipeline();

        SessionTransportHandler operate = manager.getOperateHandler(xport);
        pipeline.replace(this, operate.getName(), operate);

        SessionFrameEncoder encoder = (SessionFrameEncoder) pipeline.get("encoder");
        SessionFrameDecoder decoder = (SessionFrameDecoder) pipeline.get("decoder");

        SessionFrameOptions options = new SessionFrameOptions(xport.getNexus().getOptions());

        encoder.setOptions(options);
        decoder.setOptions(options);

        /*
         * Shut off the valve temporarily for incoming traffic until the transport successfully joins the server
         * channel. We don't have to worry about server race on the back channel. By design, the server will not
         * initiate callback until it gets the cue from the client in the form of a ping request.
         */
        channel.setReadable(false);

        // Finally start the transport
        xport.start();
    }
}
