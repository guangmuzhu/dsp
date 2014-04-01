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
import com.delphix.session.impl.frame.RequestFrame;
import com.delphix.session.impl.frame.ResponseFrame;
import com.delphix.session.impl.frame.SerialNumber;
import com.delphix.session.util.Event;

import java.util.Map;

public abstract class SessionChannel {

    protected static final Logger logger = Logger.getLogger(SessionChannel.class);

    protected final SessionNexus nexus;
    protected final boolean fore;

    protected SerialNumber commandSN;
    protected SerialNumber expectedCommandSN;

    public SessionChannel(SessionNexus nexus, boolean fore) {
        this.nexus = nexus;
        this.fore = fore;
    }

    public SessionNexus getNexus() {
        return nexus;
    }

    public boolean isFore() {
        return fore;
    }

    public SessionChannel getSibling() {
        SessionChannel sibling;

        if (isClient()) {
            sibling = nexus.getServerChannel();
        } else {
            sibling = nexus.getClientChannel();
        }

        return sibling;
    }

    public SerialNumber getCommandSN() {
        return commandSN;
    }

    public SerialNumber getExpectedCommandSN() {
        return expectedCommandSN;
    }

    /**
     * Set the expected command sequence.
     */
    public abstract void setExpectedCommandSN(SerialNumber commandSN);

    /**
     * Check if the session channel is a client channel.
     */
    public abstract boolean isClient();

    /**
     * Check if the session channel is connected.
     */
    public abstract boolean isConnected();

    /**
     * Notify the channel that the transport has just logged in and is ready to be attached. To ensure ordering of
     * transport activities, the notification must be posted from the transport context.
     */
    public void notifyAttach(final SessionTransport xport) {
        Event event = new Event(nexus.getEventSource()) {

            @Override
            public void run() {
                attach(xport);
            }
        };

        nexus.getEventManager().execute(event);
    }

    /**
     * Notify the channel that the transport has just been disconnected and must be detached. To ensure ordering of
     * transport activities, the notification must be posted from the transport context. Outstanding exchanges on
     * the transport should have all been reset by now.
     */
    public void notifyDetach(final SessionTransport xport) {
        Event event = new Event(nexus.getEventSource()) {

            @Override
            public void run() {
                detach(xport);
            }
        };

        nexus.getEventManager().execute(event);
    }

    /**
     * Shutdown the channel.
     */
    public abstract void shutdown();

    /**
     * Attach the transport to the session channel.
     */
    protected abstract void attach(SessionTransport xport);

    /**
     * Detach the transport from the session channel.
     */
    protected abstract void detach(SessionTransport xport);

    /**
     * Get the channel stats.
     */
    public abstract Map<String, ?> getStats();

    /**
     * Reset the channel stats.
     */
    public abstract void resetStats();

    /**
     * Refresh the channel state from the request.
     */
    public abstract void refresh(RequestFrame request);

    /**
     * Refresh the channel state from the response.
     */
    public abstract void refresh(ResponseFrame response);

    /**
     * Update the request with channel state.
     */
    public abstract void update(RequestFrame request);

    /**
     * Update the response with channel state.
     */
    public abstract void update(ResponseFrame response);
}
