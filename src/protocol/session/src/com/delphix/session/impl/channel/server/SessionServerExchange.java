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

package com.delphix.session.impl.channel.server;

import com.delphix.session.impl.common.SessionExchange;
import com.delphix.session.impl.common.TransportResetException;
import com.delphix.session.impl.frame.RequestFrame;

public abstract class SessionServerExchange extends SessionExchange {

    protected final SessionServerChannel channel; // Server channel

    public SessionServerExchange(SessionServerChannel channel) {
        this.channel = channel;
    }

    public SessionServerExchange(SessionServerChannel channel, RequestFrame request) {
        super(request);

        this.channel = channel;
    }

    public SessionServerChannel getChannel() {
        return channel;
    }

    @Override
    public boolean isClient() {
        return false;
    }

    @Override
    protected void setupExchange() {
        if (response != null) {
            logger.debugf("%s: exchange setup for retry", this);
            channel.update(response);
            return;
        }

        // Create the exchange
        createExchange();

        channel.update(response);
        channel.getSibling().update(response);
    }

    @Override
    public boolean send() {
        boolean status = true;

        // Set up the exchange
        setupExchange();

        // Send down the transport
        try {
            xport.sendResponse(this);
        } catch (TransportResetException e) {
            logger.errorf("%s: send failed over %s", this, xport);
            status = false;
        }

        /*
         * Reset the transport after send. Unlike the client, we never need to resend the command response over the
         * same transport on the server side. But we need to keep the primary command instance around for recovery
         * purposes. Resetting the transport allows a dead transport to be reclaimed in the meantime.
         */
        xport = null;

        return status;
    }

    @Override
    public void reset() {

    }
}
