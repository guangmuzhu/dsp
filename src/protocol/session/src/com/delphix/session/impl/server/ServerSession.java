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

import com.delphix.session.impl.channel.client.SessionClientChannel;
import com.delphix.session.impl.channel.server.SessionServerChannel;
import com.delphix.session.impl.common.LogoutFailedException;
import com.delphix.session.impl.common.SessionNexus;
import com.delphix.session.impl.frame.*;
import com.delphix.session.service.*;
import com.delphix.session.util.EventSource;
import com.delphix.session.util.ProtocolVersion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.delphix.session.service.ServiceOption.*;

/**
 * This class implements the service session interface, which is a serverside representation of the service nexus. It
 * is responsible for the lifecycle management of a session, including the creation, destroy, and ongoing maintenance
 * activities. It does so through the server session state machine which is largely driven by the explicit management
 * actions from the top such as shutdown and close as well as transport events from below such as successful login and
 * disconnect/close. The server session also serves as an event source that generates session state change events for
 * those that have expressed interests in them. In addition to the control path responsibilities, the server session
 * also manages the data path comprised of the fore channel and the back channel for command processing through the
 * common SessionNexus class shared with the client side. State changes on the control path ultimately affects how
 * the data path operates. Specifically, transport logged into the session are attached to the channels so they can
 * participate in normal command activities; and those closed are detached.
 */
public class ServerSession extends SessionNexus implements ServerNexus {

    private final ServerImpl server; // Server instance

    // Session handle
    private SessionHandle handle = ServerHandleFactory.create();

    // All transports that belong to the session
    private final List<ServerTransport> active = new LinkedList<ServerTransport>();

    // Session transports that are still in the login process
    private final List<ServerTransport> inLogin = new LinkedList<ServerTransport>();

    // Session transports that have logged in
    private final List<ServerTransport> loggedIn = new LinkedList<ServerTransport>();

    private ServerSessionState state; // Session state

    private String userName; // Authentication user name

    /*
     * Leading login indicator initialized to true for a new session and reset to false after very first successful
     * login attempt. Once reset, it indicates the session has been fully established and the state can be continued
     * even in case of transient connectivity loss.
     */
    private boolean leading = true;

    /*
     * In a session reinstatement, the successor has attempted to reinstate this session and is currently waiting for
     * it to quiesce; this session has attempted to reinstate the predecessor and is waiting for it to quiesce. This
     * could form a session reinstatement chain which hopefully would never happen.
     */
    private ServerSession predecessor; // Session reinstatement predecessor
    private ServerSession successor; // Session reinstatement successor

    private ScheduledFuture<?> timeoutFuture; // Session state timeout future
    private ServerLogoutFuture logoutFuture; // Session logout future
    private ScheduledFuture<?> logoutTimeout; // Session logout timeout future

    public ServerSession(ProtocolVersion version, ServiceTerminus client, ServerImpl server,
            Collection<? extends ProtocolHandler<?>> handlers) {
        super(client, server, handlers);

        // Set up the protocol version and service
        this.actVersion = version;
        this.server = server;

        this.executor = server.getExecutor();
        this.manager = server.getManager();

        // Initialize the session state
        setState(ServerSessionState.FREE);
    }

    public SessionHandle getHandle() {
        return handle;
    }

    public ServerSessionState getState() {
        return state;
    }

    private void setState(ServerSessionState newState) {
        ServerSessionState oldState = state;

        ServerSessionState.validate(oldState, newState);
        state = newState;

        logger.debugf("%s: state transition " + oldState + " -> " + newState, this);

        /*
         * Enqueue the state change notification with the session event manager so that it can be delivered to the
         * listeners in the same chronological order as the state change itself.
         */
        server.post(this, oldState, newState);

        /*
         * Schedule session state timeout upon entering FAILED state and canceling it upon leaving it. We promised
         * to the client that the session would be kept on the server for at least the minimum keepalive time. We
         * could hold it much longer if there is no resource pressure. But we keep it simple for now to just do the
         * minimal. In the case of a race with a previously scheduled reset future, we're well covered by the async
         * reset logic.
         */
        if (oldState == ServerSessionState.FAILED) {
            if (timeoutFuture != null) {
                timeoutFuture.cancel(false);
            }
        } else if (newState == ServerSessionState.FAILED) {
            // Close the session immediately if logout has been requested
            if (logoutFuture != null) {
                close();
                return;
            }

            timeoutFuture = schedule(new Runnable() {

                @Override
                public void run() {
                    close();
                }
            }, options.getOption(ServiceOption.MIN_KEEPALIVE_TIME), TimeUnit.SECONDS);
        }
    }

    @Override
    public EventSource getEventSource() {
        return server;
    }

    /**
     * Notify the listener of the state change event.
     */
    public void notifyListener(NexusListener listener, ServerSessionState oldState, ServerSessionState newState) {
        switch (newState) {
        case LOGGED_IN:
            if (oldState == ServerSessionState.ACTIVE) {
                listener.nexusEstablished(this);
            } else if (oldState == ServerSessionState.IN_CONTINUE) {
                listener.nexusRestored(this);
            }

            break;

        case IN_CONTINUE:
            // No externally visible state change unless it is for a newly added listener
            if (oldState != null) {
                break;
            }

        case FAILED:
            listener.nexusLost(this);
            break;

        case ZOMBIE:
            listener.nexusClosed(this);
            break;

        default:
            // The remaining session states are ignored as far as external events are concerned
            break;
        }
    }

    @Override
    public synchronized Collection<ServiceTransport> getTransports() {
        List<ServiceTransport> xports = new ArrayList<ServiceTransport>();
        xports.addAll(active);
        return xports;
    }

    @Override
    public boolean isClient() {
        return false;
    }

    @Override
    public synchronized boolean isConnected() {
        return !loggedIn.isEmpty();
    }

    @Override
    public synchronized boolean isDegraded() {
        return false;
    }

    /**
     * Notify the session of the logout request. Services interested in asynchronous logout request from the client
     * should register a nexus event listener with the server. It may then use the nexusLogout event callback as an
     * opportunity to wrap up on going activities on the session, such as outstanding commands issued over the
     * backchannel. It should also change the value of the nexus.logoutTime.server option, which is the maximum time
     * to wait before responding to the client.
     */
    public void notifyLogout(ServerLogoutFuture future) {
        synchronized (this) {
            assert logoutFuture == null : "logout already active";
            logoutFuture = future;

            // Post the session logout event
            server.post(this);

            final int timeout = options.getOption(LOGOUT_TIMEOUT);

            // Schedule logout timeout in case we don't hear from the service in time
            if (timeout > 0) {
                logoutTimeout = schedule(new Runnable() {

                    @Override
                    public void run() {
                        logout(new LogoutFailedException("logout timed out after " + timeout + " seconds"));
                    }
                }, timeout, TimeUnit.SECONDS);

                return;
            }
        }

        // Logout immediately if so desired
        logout(null);
    }

    @Override
    public synchronized boolean isLogoutRequested() {
        return logoutFuture != null;
    }

    @Override
    public synchronized boolean isLogoutComplete() {
        return logoutFuture != null && logoutFuture.isDone();
    }

    @Override
    public synchronized String getUserName() {
        return userName;
    }

    public synchronized void setUserName(String userName) {
        this.userName = userName;
    }

    @Override
    public void logout() {
        logout(null);
    }

    /**
     * Proceed to logout the session. This could be invoked by the application after it has had the chance to wrap
     * up its business or by the scheduler after logout timeout has been reached or directly from the logout request
     * processing context if no timeout is configured. Session will be left around after responding to the logout
     * request until it has lost all of its transports to accommodate logout retry.
     */
    private synchronized void logout(Throwable t) {
        // Do nothing if logout isn't active
        if (logoutFuture == null || logoutFuture.isDone()) {
            return;
        }

        // Complete the logout future
        if (t == null) {
            logoutFuture.setResult(this);

            // Cancel the login timeout
            if (logoutTimeout != null) {
                logoutTimeout.cancel(false);
            }
        } else {
            logoutFuture.setException(t);
        }
    }

    @Override
    public void stop() {
        reset();
    }

    @Override
    public Server getServer() {
        return server;
    }

    /**
     * Session login connect handler. This is invoked by the transport during stage one of the login process after
     * it has located or instantiated the session.
     */
    public boolean loginConnect(ServerTransport xport, ConnectRequest request, ConnectResponse response) {
        logger.infof("%s: %s login connect", this, xport);

        // Process session state transition
        synchronized (this) {
            switch (state) {
            case FREE:
                // N1 - The first connection in the session has reached IN_LOGIN state
                setState(ServerSessionState.ACTIVE);
                break;

            case FAILED:
                // N7 - A session continuation attempt is initiated
                setState(ServerSessionState.IN_CONTINUE);
                break;

            case IN_CONTINUE:
            case LOGGED_IN:
                // Implicit self transition
                break;

            case ZOMBIE:
                // Fail the login request if the session has been reset
                throw new LoginAbortedException();

            default:
                /*
                 * Session in ACTIVE state isn't registered with the server as it hasn't logged in yet. Such session
                 * couldn't possibly be located by the client terminus found in the connect request from a transport.
                 */
                throw new IllegalStateException("unexpected session state " + state);
            }

            // Add the transport requesting login to the session
            active.add(xport);

            // Add the transport to the transient in login list
            inLogin.add(xport);
        }

        /*
         * If this is the leading login request on a new session, as indicated by the session state transition from
         * FREE to ACTIVE, it shall be added to the server active session list. Warning is issued if a stale session
         * exists by the client terminus. But we will defer reinstatement til after the new session is logged in to
         * avoid security hole.
         */
        if (leading) {
            ServiceTerminus client = request.getClient();

            // Look for stale session registered by the same client terminus and warn if found
            ServerSession stale = server.locate(client);

            if (stale != null) {
                logger.warnf("%s: found to be stale", stale);
            }

            // Keep track of the active session in the server
            server.attach(this);
        }

        // Set the response status to success
        response.setStatus(LoginStatus.SUCCESS);

        return true;
    }

    /**
     * Session login negotiation handler. This is invoked by the transport during stage four of the login process
     * after negotiate request has been received from the client.
     */
    public boolean loginNegotiate(ServerTransport xport, NegotiateRequest request, NegotiateResponse response) {
        logger.infof("%s: %s login negotiate", this, xport);

        // Negotiate the protocol options based on the server offer and the client proposal
        ServiceOptions offer = xport.getOptions().propose();

        synchronized (this) {
            switch (state) {
            case ACTIVE:
                offer.override(options.propose());
                break;

            default:
                break;
            }

            /*
             * Make sure the maximum connection limit has not been exceeded. Otherwise, fail the login attempt
             * with connection exceeded status and close the connection.
             */
            if (loggedIn.size() >= options.getOption(MAX_TRANSPORTS)) {
                response.setStatus(LoginStatus.CONNECTION_EXCEEDED);
                return false;
            }
        }

        ServiceOptions result;

        try {
            ServiceOptions proposal = ServiceOptions.fromString(request.getParams());
            result = offer.negotiate(proposal);
        } catch (IllegalArgumentException e) {
            logger.errorf("%s: parameter negotiation failure - proposal %s, offer %s", this,
                    request.getParams(), offer.toString());
            response.setStatus(LoginStatus.PARAMETER_UNSUPPORTED);
            return false;
        }

        response.setParams(result.toString());

        // Process session state transition
        synchronized (this) {
            switch (state) {
            case ACTIVE:
                // Set the negotiated protocol options for the session
                options.override(result.getNexusOptions());

                // Bring up the session fore channel and back channel
                int foreQueue = options.getOption(FORE_QUEUE_DEPTH);
                int backQueue = options.getOption(BACK_QUEUE_DEPTH);
                int bandwidthLimit = options.getOption(BANDWIDTH_LIMIT);

                serverChannel = new SessionServerChannel(this, true, foreQueue, request.getCommandSN());
                clientChannel = new SessionClientChannel(this, false, backQueue, bandwidthLimit);

                // N2 - At least one connection reached the LOGGED_IN state
                setState(ServerSessionState.LOGGED_IN);

                /*
                 * If this is the leading login request on a new session, as indicated by the session state transition
                 * from ACTIVE to LOGGED_IN, the session shall be taken out of the transient server active session
                 * list before it is registered with the server. Session registration must be done before we respond
                 * to the client since a login request over a different transport may arrive at any time after that.
                 * We will defer session reinstatement til later to avoid a race with it.
                 */
                if (leading) {
                    predecessor = server.register(this);
                    leading = false;
                }

                break;

            case IN_CONTINUE:
                // N10 - A session continuation attempt succeeded
                setState(ServerSessionState.LOGGED_IN);
                break;

            case LOGGED_IN:
                // Implicit self transition
                break;

            case ZOMBIE:
                // Force transport disconnect due to race with session reset
                throw new LoginAbortedException();

            default:
                throw new IllegalStateException("unexpected session state " + state);
            }

            // Move the transport from the in login list to the logged in list
            boolean removed = inLogin.remove(xport);
            assert removed;
            boolean added = loggedIn.add(xport);
            assert added;
        }

        // Refresh the channels with vital info found in the request
        serverChannel.refresh(request);
        clientChannel.refresh(request);

        // Update the response with vital channel info
        serverChannel.update(response);
        clientChannel.update(response);

        // Set the negotiated protocol options for the transport
        xport.getOptions().override(result.getTransportOptions());

        // Set the response status to success
        response.setStatus(LoginStatus.SUCCESS);

        return true;
    }

    /**
     * Session login completion handler. This is invoked by the transport during the final stage of the login process
     * after negotiate response has been sent and the transport is ready for incoming commands.
     */
    public synchronized void loginComplete(ServerTransport xport) {
        logger.infof("%s: %s login complete", this, xport);

        /*
         * In case the session registration hit a stale session by the same client terminus, a session reinstatement
         * is initiated here and we will defer transport channel attach until the wait for reinstatement is over.
         */
        if (predecessor != null) {
            if (!predecessor.reinstate(this)) {
                return;
            }

            // Reset the predecessor if the session reinstatement finished synchronously
            predecessor = null;
        }

        // Notify the server channel
        serverChannel.notifyAttach(xport);
    }

    /**
     * Enable the transport for client channel operation. This is invoked by the transport after the ping is received
     * from the client immediately after login. It signals the client is ready to accept incoming commands.
     */
    public synchronized void enableClient(ServerTransport xport) {
        logger.infof("%s: %s enabled for client channel", this, xport);

        // Set under the session lock to prevent a race with reinstatement
        xport.setClientReady();

        // Defer transport attach until the wait for reinstatement is over
        if (predecessor != null) {
            return;
        }

        // Notify the client channel
        clientChannel.notifyAttach(xport);
    }

    /**
     * Notify the session the specified transport has been closed.
     */
    public void notifyClosed(ServerTransport xport) {
        logger.infof("%s: %s closed", this, xport);

        // Process session state transition
        synchronized (this) {
            boolean removed = true;
            switch (state) {
            case ACTIVE:
                removed = inLogin.remove(xport);
                assert removed;

                if (inLogin.isEmpty()) {
                    // N9 - Login attempt on the leading connection failed
                    setState(ServerSessionState.FREE);
                }

                break;

            case IN_CONTINUE:
                removed = inLogin.remove(xport);
                assert removed;

                if (inLogin.isEmpty()) {
                    // N8 - The last session continuation attempt failed
                    setState(ServerSessionState.FAILED);
                }

                break;

            case LOGGED_IN:
                if (inLogin.remove(xport)) {
                    break;
                }

                removed = loggedIn.remove(xport);
                assert removed;

                if (loggedIn.isEmpty()) {
                    /*
                     * N5 - Session failure occurred whence the last operational transport has been closed due to
                     * abnormal causes and all outstanding commands start to wait for recovery.
                     */
                    setState(ServerSessionState.FAILED);

                    // N7 - A session continuation attempt is initiated
                    if (!inLogin.isEmpty()) {
                        setState(ServerSessionState.IN_CONTINUE);
                    }
                }

                // Notify the channels
                if (predecessor == null) {
                    serverChannel.notifyDetach(xport);

                    if (xport.isClientReady()) {
                        clientChannel.notifyDetach(xport);
                    }
                }

                break;

            case ZOMBIE:
                if (!inLogin.remove(xport)) {
                    removed = loggedIn.remove(xport);
                    assert removed;

                    // Notify the channels
                    if (predecessor == null) {
                        serverChannel.notifyDetach(xport);

                        if (xport.isClientReady()) {
                            clientChannel.notifyDetach(xport);
                        }
                    }
                }

                removed = active.remove(xport);
                assert removed;

                if (active.isEmpty()) {
                    initiateShutdown();
                }

                return;

            default:
                throw new IllegalStateException("unexpected session state " + state);
            }

            // Remove the transport from the session
            removed = active.remove(xport);
            assert removed;
        }

        /*
         * The session is attached to the server when the leading login is received. We need to undo that now since
         * the leading login has failed.
         */
        if (leading) {
            server.detach(this);
        }
    }

    private void reset() {
        List<ServerTransport> xports = new ArrayList<ServerTransport>();

        if (isLogoutComplete()) {
            logger.infof("%s: session closed", this);
        } else {
            logger.errorf("%s: session reset", this);
        }

        synchronized (this) {
            // Reset only if it has not been
            if (state == ServerSessionState.ZOMBIE) {
                return;
            }

            // Detach from the server now if the session state is ACTIVE
            if (state == ServerSessionState.ACTIVE) {
                server.detach(this);
            }

            // N3, N6, N11
            setState(ServerSessionState.ZOMBIE);

            if (logoutFuture != null && !logoutFuture.isDone()) {
                logoutFuture.setException(new LogoutFailedException());
            }

            if (timeoutFuture != null) {
                timeoutFuture.cancel(false);
            }

            if (active.isEmpty()) {
                initiateShutdown();
                return;
            }

            // Get a list of all active transports
            xports.addAll(active);
        }

        for (ServerTransport xport : xports) {
            xport.close();
        }
    }

    private void initiateShutdown() {
        // Defer disposal until session reinstatement is over
        if (predecessor != null) {
            return;
        }

        execute(new Runnable() {

            @Override
            public void run() {
                shutdown();
            }
        });
    }

    private void shutdown() {
        logger.infof("%s: session shutdown", this);

        // Flush pending events
        server.getManager().getEventManager().flush(server, false);

        // Shutdown the client channel
        if (clientChannel != null) {
            clientChannel.shutdown();
        }

        // Shutdown the server channel
        if (serverChannel != null) {
            serverChannel.shutdown();
        }

        // Unregister the session and notify the server
        if (server.locate(handle) != null) {
            server.unregister(this);
        }

        // Notify the successor if involved in a session reinstatement
        ServerSession session;

        synchronized (this) {
            // Reset the session handle to avoid racing with session reinstatement
            handle = null;

            // Get the successor
            session = successor;
        }

        if (session != null) {
            session.notifyReinstated();
        }

        logger.infof("%s: session disposed", this);

        // Mark the nexus closed after shutdown is complete
        closeFuture.setResult(this);
    }

    /**
     * Reinstate the session with the given successor. Session reinstatement requires all activities on the target
     * session to be fully quiesced. In that sense, it is similar to session close or reset, except the latter is
     * driven by internal events whereas session reinstatement by login from the client. Session reinstatement is
     * asynchronous. After the session is reinstated, a notification is delivered to the successor.
     */
    private boolean reinstate(ServerSession session) {
        logger.infof("%s: session reinstatement from %s", this, session);

        synchronized (this) {
            /*
             * The session to be reinstated has already been disposed of, as indicated by the null session handle.
             * In this case, session reinstatement may complete synchronously.
             */
            if (handle == null) {
                return true;
            }

            // Set up the successor
            successor = session;

            // Post a nexus reinstatement event
            server.post(this, session);
        }

        // Close this session
        close();

        return false;
    }

    /**
     * Notify the successor in the session reinstatement that the predecessor is now disposed of.
     *
     * In a recursive session reinstatement, the session we (i.e., this session) are trying to reinstate just got
     * disposed of. But before that, we got reinstated by another newer session. We should have been reset by the
     * new successor in that case. We just need to initiate disposal if active transports have all been closed.
     */
    private synchronized void notifyReinstated() {
        logger.infof("%s: session reinstatement done", this);

        // Reset the predecessor
        predecessor = null;

        // Attach all logged in transports to the channels
        for (ServerTransport xport : loggedIn) {
            serverChannel.notifyAttach(xport);

            if (xport.isClientReady()) {
                clientChannel.notifyAttach(xport);
            }
        }

        // Initiate shutdown if the session has been reset and lost all active transports
        if (state == ServerSessionState.ZOMBIE) {
            logger.infof("%s: session already reset", this);

            if (active.isEmpty()) {
                initiateShutdown();
            }
        }
    }

    @Override
    public String toString() {
        return "nexus-s:" + client + "-" + server.getTerminus();
    }
}
