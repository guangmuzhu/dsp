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

import com.delphix.session.impl.frame.SessionFrameDecoder;
import com.delphix.session.impl.frame.SessionFrameEncoder;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelLocal;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;

public abstract class SessionTransportManager {

    /*
     * Channel local facilitates global channel to transport mapping. The mapping is created when the channel is
     * opened and removed when channel is closed. The automatic remove-on-close feature is not used because it
     * causes the mapping to removed before the channel disconnect and close events are delivered upstream in the
     * channel (see netty-3.2.7 NioWorker.close for reference) and the mapping is still needed for the handling of
     * those events. The channel local facility is thread-safe.
     */
    protected final ChannelLocal<SessionTransport> transports = new ChannelLocal<SessionTransport>(false);

    /*
     * Channel group supports the remove-on-close semantics, which as of netty-3.2.7, implies that the channel is
     * removed from the group before exception, disconnect, and close events are delivered upstream in the channel.
     * The channel group facility is thread-safe.
     */
    protected final ChannelGroup channels = new DefaultChannelGroup();

    protected ChannelFactory factory; // Channel factory

    protected SessionTransportHandler authenticateHandler;
    protected SessionTransportHandler negotiateHandler;

    public SessionTransportManager() {

    }

    public void shutdown() {
        // Force close all channels
        channels.close().awaitUninterruptibly();

        // Release external resources
        factory.releaseExternalResources();
    }

    public SessionFrameEncoder getFrameEncoder() {
        return new SessionFrameEncoder(this);
    }

    public SessionFrameDecoder getFrameDecoder() {
        return new SessionFrameDecoder(this);
    }

    /**
     * Get the channel handler for the session login connect phase.
     */
    public abstract SessionTransportHandler getConnectHandler(SessionTransport xport);

    /**
     * Get the channel handler for the session login authenticate phase.
     */
    public SessionTransportHandler getAuthenticateHandler() {
        return authenticateHandler;
    }

    /**
     * Get the channel handler for the session login negotiate phase.
     */
    public SessionTransportHandler getNegotiateHandler() {
        return negotiateHandler;
    }

    /**
     * Get the channel handler for the session normal operating phase after successful login.
     */
    public abstract SessionTransportHandler getOperateHandler(SessionTransport xport);

    /**
     * Add the newly opened channel to the group and attach the transport with it.
     */
    public void attach(Channel channel, SessionTransport xport) {
        transports.set(channel, xport);
        channels.add(channel);
    }

    /**
     * Detach the transport from the channel.
     */
    public SessionTransport detach(Channel channel) {
        return transports.remove(channel);
    }

    /**
     * Locate the transport for the channel.
     */
    public SessionTransport locate(Channel channel) {
        return transports.get(channel);
    }
}
