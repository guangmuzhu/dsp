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

import com.delphix.session.impl.client.ClientLogoutFuture;
import com.delphix.session.impl.frame.LogoutRequest;
import com.delphix.session.impl.frame.LogoutResponse;

public class SessionClientLogout extends SessionClientExchange {

    private final ClientLogoutFuture future; // Logout task future
    private final boolean logoutSession; // Session or transport logout

    public SessionClientLogout(SessionClientChannel channel, ClientLogoutFuture future) {
        super(channel);

        this.future = future;

        // Only session logout is supported
        logoutSession = true;
    }

    public ClientLogoutFuture getFuture() {
        return future;
    }

    @Override
    protected void createExchange() {
        LogoutRequest request = new LogoutRequest();

        request.setExchangeID();
        request.setLogoutSession(logoutSession);

        setRequest(request);
    }

    @Override
    public void receive() {
        if (!(response instanceof LogoutResponse)) {
            handleInvalidResponse();
            return;
        }

        channel.refresh(response);
        channel.getSibling().refresh(response);

        // Send the logout to the channel for completion
        channel.exchangeDone(this);
    }

    @Override
    public void reset() {
        logger.errorf("%s: failed with %s over %s", this, status, xport);

        // Send the logout to the channel for reset
        channel.exchangeDone(this);
    }

    /**
     * Set the logout future result or exception to set off logout complete notification to the session.
     */
    public void complete(Throwable t) {
        if (t != null) {
            future.setException(t);
        } else {
            future.setResult(future.getNexus());
        }
    }

    @Override
    public String toString() {
        return super.toString() + "{" + logoutSession + "}";
    }
}
