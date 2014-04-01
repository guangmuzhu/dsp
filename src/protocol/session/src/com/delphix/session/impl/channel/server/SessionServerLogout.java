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

import com.delphix.session.impl.frame.LogoutRequest;
import com.delphix.session.impl.frame.LogoutResponse;
import com.delphix.session.impl.frame.LogoutStatus;
import com.delphix.session.impl.frame.RequestFrame;
import com.delphix.session.impl.server.ServerLogoutFuture;
import com.delphix.session.impl.server.ServerSession;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

public class SessionServerLogout extends SessionServerExchange {

    private final ServerLogoutFuture future; // Logout future

    private SessionServerLogout primary; // Primary logout
    private Queue<SessionServerLogout> retryQueue; // Pending retry queue

    public SessionServerLogout(SessionServerChannel channel, RequestFrame request) {
        super(channel, request);

        future = new ServerLogoutFuture((ServerSession) channel.getNexus());
    }

    public void setPrimary(SessionServerLogout primary) {
        this.primary = primary;
    }

    public SessionServerLogout getPrimary() {
        return primary;
    }

    public void offerRetry(SessionServerLogout logout) {
        if (retryQueue == null) {
            retryQueue = new LinkedList<SessionServerLogout>();
        }

        retryQueue.offer(logout);
    }

    public SessionServerLogout pollRetry() {
        SessionServerLogout logout = null;

        if (retryQueue != null) {
            logout = retryQueue.poll();
        }

        return logout;
    }

    public boolean hasRetry() {
        return retryQueue != null && !retryQueue.isEmpty();
    }

    @Override
    protected void createExchange() {
        SessionServerLogout logout;

        if (primary != null) {
            logger.debugf("%s: logout setup for retry", this);
            logout = primary;
        } else {
            logout = this;
        }

        LogoutResponse response = new LogoutResponse();

        response.setForeChannel(channel.isFore());

        response.setExchangeID(exchangeID);

        ServerLogoutFuture future = logout.getFuture();
        LogoutStatus status = LogoutStatus.SUCCESS;

        try {
            future.get();
        } catch (InterruptedException e) {
            assert false;
        } catch (CancellationException e) {
            assert false;
        } catch (ExecutionException e) {
            logger.errorf(e, "%s: logout failed", this);
            status = LogoutStatus.LOGOUT_FAILED;
        }

        response.setStatus(status);

        setResponse(response);
    }

    private ServerLogoutFuture getFuture() {
        return future;
    }

    @Override
    public void receive() {
        channel.refresh(request);
        channel.getSibling().refresh(request);

        channel.exchangeReceived(this);
    }

    public void invoke() {
        channel.getNexus().execute(future);
    }

    @Override
    public String toString() {
        LogoutRequest request = (LogoutRequest) this.request;
        return super.toString() + "{" + request.isLogoutSession() + "}";
    }
}
