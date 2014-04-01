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

package com.delphix.session.impl.channel.client;

import com.delphix.session.impl.common.ProtocolViolationException;
import com.delphix.session.impl.common.SessionExchange;
import com.delphix.session.impl.common.SessionTransportStatus;
import com.delphix.session.impl.common.TransportResetException;
import com.delphix.session.impl.frame.ResponseFrame;

public abstract class SessionClientExchange extends SessionExchange {

    protected final SessionClientChannel channel; // Client channel

    public SessionClientExchange(SessionClientChannel channel) {
        this.channel = channel;
    }

    public SessionClientChannel getChannel() {
        return channel;
    }

    @Override
    public boolean isClient() {
        return true;
    }

    @Override
    protected void setupExchange() {
        if (request != null) {
            logger.debugf("%s: exchange setup for retry", this);
            channel.update(request);
            return;
        }

        // Create the exchange
        createExchange();

        channel.update(request);
        channel.getSibling().update(request);
    }

    @Override
    public boolean send() {
        boolean status = true;

        // Set up the exchange
        setupExchange();

        // Send down the transport
        try {
            xport.sendRequest(this);
        } catch (TransportResetException e) {
            logger.errorf("%s: send failed over %s", this, xport);
            status = false;
        }

        return status;
    }

    /**
     * It is protocol violation if the response isn't what we are expecting. We must throw an exception to reset the
     * transport. Before that, we have to hand the command back to the channel so we don't lose track of it. For this
     * command, we will pretend the connection has just been reset.
     */
    protected void handleInvalidResponse() {
        ResponseFrame response = this.response;

        setStatus(SessionTransportStatus.CONN_RESET);
        this.response = null;

        reset();

        throw new ProtocolViolationException("invalid response " + response);
    }
}
