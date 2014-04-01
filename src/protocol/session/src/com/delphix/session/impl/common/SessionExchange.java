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
import com.delphix.session.impl.frame.ExchangeID;
import com.delphix.session.impl.frame.RequestFrame;
import com.delphix.session.impl.frame.ResponseFrame;

public abstract class SessionExchange {

    protected static final Logger logger = Logger.getLogger(SessionExchange.class);

    protected SessionTransport xport; // Current transport in use

    protected ExchangeID exchangeID; // Exchange ID

    protected RequestFrame request; // Request wire frame
    protected ResponseFrame response; // Response wire frame

    protected SessionTransportStatus status; // Transport status

    protected SessionExchange dependency; // Dependency exchange

    public SessionExchange() {

    }

    public SessionExchange(RequestFrame request) {
        setRequest(request);
    }

    public SessionTransport getTransport() {
        return xport;
    }

    public void setTransport(SessionTransport xport) {
        this.xport = xport;
    }

    public ExchangeID getExchangeID() {
        return exchangeID;
    }

    public void resetTransport() {
        setTransport(null);
    }

    public RequestFrame getRequest() {
        return request;
    }

    public void setRequest(RequestFrame request) {
        this.request = request;
        this.exchangeID = request.getExchangeID();
    }

    public ResponseFrame getResponse() {
        return response;
    }

    /**
     * Subclass should override this method to provide response type validation.
     */
    public void setResponse(ResponseFrame response) {
        this.response = response;
    }

    public SessionTransportStatus getStatus() {
        return status;
    }

    public void setStatus(SessionTransportStatus status) {
        this.status = status;
    }

    public void resetStatus() {
        setStatus(null);
    }

    public SessionExchange getDependency() {
        return dependency;
    }

    public void setDependency(SessionExchange dependent) {
        this.dependency = dependent;
    }

    /**
     * Check if this is a client exchange.
     */
    public abstract boolean isClient();

    /**
     * Set up the exchange so that it is ready to be sent to the remote peer.
     */
    protected abstract void setupExchange();

    /**
     * Create the over-the-wire session frame to be sent to the remote peer.
     */
    protected abstract void createExchange();

    /**
     * Send the session exchange.
     */
    public abstract boolean send();

    /**
     * Receive the session exchange.
     */
    public abstract void receive();

    /**
     * Reset the session exchange due to transport reset or task management.
     */
    public abstract void reset();

    @Override
    public String toString() {
        return exchangeID != null ? exchangeID.toString() : super.toString();
    }
}
