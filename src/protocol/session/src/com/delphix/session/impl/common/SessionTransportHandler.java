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

package com.delphix.session.impl.common;

import com.delphix.appliance.logger.Logger;
import org.jboss.netty.channel.*;

public class SessionTransportHandler extends SimpleChannelUpstreamHandler {

    protected static final Logger logger = Logger.getLogger(SessionTransportHandler.class);

    protected final SessionTransportManager manager;
    protected final String name;

    protected SessionTransportHandler(SessionTransportManager manager, String name) {
        this.manager = manager;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        if (e instanceof ChannelStateEvent) {
            // Log the channel state events that are interesting
            ChannelStateEvent stateEvent = (ChannelStateEvent) e;

            switch (stateEvent.getState()) {
            case OPEN:
            case BOUND:
                logger.debug(e);
                break;

            case CONNECTED:
                logger.info(e);
                break;

            case INTEREST_OPS:
                break;
            }
        }

        // Rely on the super class for the actual channel event dispatching
        super.handleUpstream(ctx, e);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        Channel channel = ctx.getChannel();
        SessionTransport xport = manager.locate(channel);

        logger.error(e.getCause(), "unexpected exception from downstream");

        if (channel.isConnected()) {
            xport.close();
        }
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        Channel channel = ctx.getChannel();
        SessionTransport xport = manager.locate(channel);

        xport.notifyDisconnected();
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        Channel channel = ctx.getChannel();
        SessionTransport xport = manager.detach(channel);

        xport.notifyClosed();
    }
}
