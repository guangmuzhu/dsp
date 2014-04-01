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

package com.delphix.session.impl.client;

import com.delphix.appliance.server.util.ExceptionUtil;
import com.delphix.session.impl.channel.client.SessionClientChannel;
import com.delphix.session.impl.channel.server.SessionServerChannel;
import com.delphix.session.impl.common.ProtocolViolationException;
import com.delphix.session.impl.common.SessionEventDispatcher;
import com.delphix.session.impl.common.SessionEventSource;
import com.delphix.session.impl.common.SessionNexus;
import com.delphix.session.impl.frame.*;
import com.delphix.session.sasl.ClientSaslMechanism;
import com.delphix.session.service.*;
import com.delphix.session.ssl.SSLClientContext;
import com.delphix.session.util.Event;
import com.delphix.session.util.EventSource;
import com.delphix.session.util.ProtocolVersion;

import javax.net.ssl.SSLEngine;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

import java.util.*;
import java.util.concurrent.*;

import static com.delphix.session.service.ServiceOption.*;
import static com.delphix.session.service.ServiceProtocol.PROTOCOL;

/**
 * This class implements the service client interface, which is a client side representation of the service nexus. It
 * is responsible for the lifecycle management of a session, including the creation, destroy, and ongoing maintenance
 * activities. It does so through the client session state machine which is largely driven by the explicit management
 * actions from the top such as create and close as well as transport events from below such as successful login and
 * disconnect/close. The client session also serves as an event source that generates session state change events for
 * those that have expressed interests in them. In addition to the control path responsibilities, the client session
 * also manages the data path comprised of the fore channel and the back channel for command processing through the
 * common SessionNexus class shared with the server side. State changes on the control path ultimately affects how
 * the data path operates. Specifically, transport logged into the session are attached to the channels so they can
 * participate in normal command activities; and those closed are detached.
 */
public class ClientSession extends SessionNexus implements ClientNexus, SessionEventSource {

    private final SessionEventDispatcher dispatcher; // Event dispatcher

    private final ClientConfig spec; // Service spec

    private ClientLoginFuture loginFuture; // Session login future
    private ClientLogoutFuture logoutFuture; // Session logout future
    private ScheduledFuture<?> timeoutFuture; // Session timeout future
    private ScheduledFuture<?> recoveryFuture; // Session recovery future

    private ProtocolVersion maxVersion; // Maximum protocol version supported by server

    private SessionHandle handle; // Session handle

    private ClientSessionState state; // Session state

    // Active transports - all except zombie
    private final List<ClientTransport> active = new LinkedList<ClientTransport>();

    // Session transports that are in the login process
    private final List<ClientTransport> inLogin = new LinkedList<ClientTransport>();

    // Session transports that have logged in
    private final List<ClientTransport> loggedIn = new LinkedList<ClientTransport>();

    // Session transports unbound to the channel and waiting for recovery
    private final List<ClientTransport> unbound = new LinkedList<ClientTransport>();

    // Session transports that have experienced irrecoverable failures
    private final List<ClientTransport> zombie = new LinkedList<ClientTransport>();

    public ClientSession(ClientManagerImpl manager, ClientConfig spec) {
        super(spec);

        // We will use the default executor if the service did not bother to specify its own
        ExecutorService executor = spec.getExecutor();

        if (executor == null) {
            executor = manager.getExecutionManager();
        }

        this.executor = executor;
        this.manager = manager;

        this.spec = spec;

        List<TransportAddress> addrs = spec.getAddresses();

        for (TransportAddress addr : addrs) {
            ClientTransport xport = new ClientTransport(addr, this);

            active.add(xport);
            unbound.add(xport);
        }

        dispatcher = new SessionEventDispatcher(this, manager.getEventManager());

        // Initialize the session state
        setState(ClientSessionState.FREE);
    }

    public ProtocolVersion getMaxVersion() {
        return maxVersion;
    }

    public SessionHandle getHandle() {
        return handle;
    }

    public ClientSessionState getState() {
        return state;
    }

    private void setState(ClientSessionState newState) {
        ClientSessionState oldState = state;

        ClientSessionState.validate(oldState, newState);
        state = newState;

        logger.debugf("%s: state transition " + oldState + " -> " + newState, this);

        /*
         * Post the state change notification with the session event handler so that it can be delivered to the
         * session listeners in the same chronological order as the state change itself.
         */
        dispatcher.post(new StateChangeEvent(oldState, newState));
    }

    private ClientManagerImpl getClientManager() {
        return (ClientManagerImpl) manager;
    }

    @Override
    public synchronized Collection<ServiceTransport> getTransports() {
        List<ServiceTransport> xports = new ArrayList<ServiceTransport>();

        xports.addAll(active);
        xports.addAll(zombie);

        return xports;
    }

    @Override
    public boolean isClient() {
        return true;
    }

    @Override
    public synchronized boolean isConnected() {
        return !loggedIn.isEmpty();
    }

    @Override
    public synchronized boolean isDegraded() {
        return !zombie.isEmpty() || active.size() > loggedIn.size();
    }

    @Override
    public ClientConfig getConfig() {
        return spec;
    }

    @Override
    public LoginFuture login() {
        synchronized (this) {
            if (loginFuture != null) {
                return loginFuture;
            }

            loginFuture = new ClientLoginFuture(this);
        }

        // Execute the login future to start the session
        execute(loginFuture);

        return loginFuture;
    }

    public synchronized void start() {
        // N2 - Session login has been initiated
        setState(ClientSessionState.ACTIVE);

        final long timeout = spec.getLoginTimeout();

        if (timeout > 0) {
            timeoutFuture = schedule(new Runnable() {

                @Override
                public void run() {
                    close(new LoginTimeoutException("login timed out after " + timeout + " seconds"));
                }
            }, timeout, TimeUnit.SECONDS);
        }

        // Kick start the session login process via the common recovery mechanism
        scheduleRecovery();
    }

    @Override
    public void stop() {
        Throwable t = closeFuture.getCause();

        synchronized (this) {
            // Cancel future session recovery
            if (recoveryFuture != null) {
                recoveryFuture.cancel(false);
            }

            /*
             * Attempt graceful close (a.k.a., logout) if both conditions below are satisfied
             *
             *  1) session logged in previously
             *  2) session closing under normal circumstances
             */
            if (t == null && loginFuture != null && loginFuture.isDone()) {
                ClientNexus nexus = null;

                try {
                    nexus = loginFuture.get();
                } catch (Exception e) {
                    // Ignore the exception
                    logger.errorf(e, "%s: logout skipped due to login failure", this);
                }

                if (nexus != null) {
                    logoutFuture = new ClientLogoutFuture(this);
                }
            }
        }

        // Execute the logout future to request logout
        if (logoutFuture != null) {
            execute(logoutFuture);
            return;
        }

        // Reset the session immediately if logout is to be skipped
        reset(t);
    }

    public void logout() {
        /*
         * It is possible that the login is marked complete while the transports are still waiting to attach to the
         * channels. Since the transport attachment is processed as event, we shall flush pending events on the
         * session to ensure all logged in transports are attached before issuing logout.
         */
        getEventManager().flush(this, false);

        // Issue logout request to the server
        clientChannel.logout(logoutFuture);
    }

    public void logoutComplete() {
        Throwable t = null;

        try {
            logoutFuture.get();
        } catch (InterruptedException e) {
            // Logout is already complete
            assert false : "unexpected interrupt";
        } catch (CancellationException e) {
            // Logout is not cancelled from the client
            assert false : "unexpected cancellation";
        } catch (ExecutionException e) {
            t = ExceptionUtil.unwrap(e);
        }

        // Reset the session now that the logout has been attempted
        reset(t);
    }

    /**
     * Create a SSL engine to be used for the TLS/SSL handshake or null if TLS is not requested.
     */
    public SSLEngine createSslEngine() {
        SSLClientContext ssl = spec.getSslContext();

        if (ssl == null) {
            return null;
        }

        return ssl.create();
    }

    /**
     * Create a SASL client for authentication.
     */
    public SaslClient createSaslClient(ClientTransport xport) {
        ClientSaslMechanism mechanism = spec.getSaslMechanism();
        SaslClient sasl = null;

        try {
            sasl = mechanism.create(PROTOCOL, spec.getServerHost(), null);
        } catch (SaslException e) {
            loginFailed(xport, new AuthFailedException("failed to create sasl client"));
        }

        return sasl;
    }

    /**
     * Construct the connect request for stage one of the login process. The information exchanged at this stage
     * includes
     *
     *   - the protocol version range
     *   - the client and service terminus
     *   - SSL support
     *   - supported SASL mechanisms
     *   - session handle
     */
    public ConnectRequest loginConnect(ClientTransport xport) {
        ConnectRequest request = new ConnectRequest();

        request.setExchangeID();

        // Set the terminus at either end of the session
        request.setClient(client);
        request.setServer(server);

        // Set the session handle or null if this is the leading transport
        request.setSessionHandle(handle);

        // Set the acceptable protocol version range
        request.setMaxVersion(manager.getMaxVersion());
        request.setMinVersion(manager.getMinVersion());

        // Configure the TLS setting
        SSLClientContext ssl = spec.getSslContext();

        if (ssl != null) {
            request.setTlsLevel(ssl.getTlsLevel());
        }

        return request;
    }

    /**
     * Process the connect response. In case of login failure, either due to server rejection or protocol violation,
     * throw an exception to cause the login process to be aborted and the underlying transport closed.
     */
    public void loginConnect(ClientTransport xport, ConnectResponse response) {
        ProtocolVersion actVersion = response.getActVersion();
        ProtocolVersion maxVersion = response.getMaxVersion();

        // The server selected active version must be less than or equal to the server supported maximum version
        if (actVersion.greaterThan(maxVersion)) {
            throw new ProtocolViolationException("invalid version range " + actVersion + " to " + maxVersion);
        }

        // The server selected active version must be within the protocol version range the client supports
        if (actVersion.greaterThan(manager.getMaxVersion()) || actVersion.lessThan(manager.getMinVersion())) {
            throw new ProtocolViolationException("active version " + actVersion + " out of range " +
                    manager.getMinVersion() + " to " + manager.getMaxVersion());
        }

        SessionHandle handle = response.getSessionHandle();

        synchronized (this) {
            switch (state) {
            case ACTIVE:
                /*
                 * Set the session properties acquired from the server in the leading login. It is only tentative
                 * until the leading login is completed successful and reset if it fails at a later stage.
                 */
                this.actVersion = actVersion;
                this.maxVersion = maxVersion;
                this.handle = handle;

                break;

            case LOGGED_IN:
            case FAILED:
                // Validate the active version has not changed since the session was established
                if (!this.actVersion.equals(actVersion)) {
                    throw new ProtocolViolationException("version changed " + this.actVersion + " to " + actVersion);
                }

                // Similar for maximum protocol version except change is tolerated
                if (!this.maxVersion.equals(maxVersion)) {
                    logger.warnf("%s: protocol maximum version changed from %s to %s",
                            this, this.maxVersion, maxVersion);
                }

                // Validate the session handle match the existing one in the session
                if (!this.handle.equals(handle)) {
                    throw new ProtocolViolationException("session handle changed " + this.handle + " to " + handle);
                }

                break;

            case ZOMBIE:
                throw new LoginAbortedException();

            default:
                throw new IllegalStateException("invalid session state " + state);
            }
        }

        /*
         * Verify the client specified SASL mechanism is supported by the server. For this to work, the client must
         * have knowledge a priori on what authentication mechanisms the server supports. The knowledge is expected
         * to be acquired out of band.
         */
        List<String> mechanisms = response.getSaslMechanisms();
        ClientSaslMechanism mechanism = spec.getSaslMechanism();

        if (!mechanisms.contains(mechanism.getMechanism())) {
            loginFailed(xport, new AuthNotSupportedException(mechanism.getMechanism()));
        }
    }

    /**
     * Start the authenticate process by creating the initial authenticate request. Failure to do so will lead to
     * session reset.
     */
    public AuthenticateRequest loginAuthenticate(ClientTransport xport) {
        SaslClient sasl = xport.getSaslClient();

        // Evaluate the initial SASL response
        byte[] saslResponse = null;

        try {
            if (sasl.hasInitialResponse()) {
                saslResponse = sasl.evaluateChallenge(new byte[0]);
            } else {
                saslResponse = new byte[0];
            }
        } catch (SaslException e) {
            loginFailed(xport, new AuthFailedException("failed to evaluate sasl challenge"));
        }

        // Construct the initial authenticate request
        AuthenticateRequest request = new AuthenticateRequest();

        request.setExchangeID();

        request.setInitial(true);
        request.setMechanism(sasl.getMechanismName());
        request.setResponse(saslResponse);

        return request;
    }

    /**
     * Process the authenticate response. Return the authenticate request for the next round or null if the sasl
     * authentication is complete. Authentication failure will lead to session reset.
     */
    public AuthenticateRequest loginAuthenticate(ClientTransport xport, AuthenticateResponse response) {
        SaslClient sasl = xport.getSaslClient();

        // Sanity check the SASL negotiation state
        byte[] saslChallenge = response.getChallenge();

        if (sasl.isComplete()) {
            // No more challenge from the server if SASL negotiation is complete on the client
            if (saslChallenge != null) {
                throw new ProtocolViolationException("unexpected sasl challenge");
            } else if (!response.isComplete()) {
                throw new ProtocolViolationException("sasl server still incomplete");
            }

            return null;
        } else {
            // Server must send a SASL challenge if the SASL negotiation is incomplete on the client
            if (saslChallenge == null) {
                throw new ProtocolViolationException("missing sasl challenge");
            }
        }

        // Evaluate the SASL response
        byte[] saslResponse = null;

        try {
            saslResponse = sasl.evaluateChallenge(saslChallenge);
        } catch (SaslException e) {
            loginFailed(xport, new AuthFailedException("failed to evaluate sasl challenge"));
        }

        // Sanity check the SASL negotiation state
        if (response.isComplete()) {
            // No more SASL response if the SASL negotiation is complete on the server
            if (saslResponse != null) {
                throw new ProtocolViolationException("unexpected sasl response");
            }

            // The SASL negotiation must be complete on the client if it is on the server
            if (!sasl.isComplete()) {
                throw new ProtocolViolationException("sasl client still incomplete");
            }

            return null;
        }

        // Prepare the next response in case SASL negotiation is incomplete
        AuthenticateRequest request = new AuthenticateRequest();

        request.setExchangeID();

        request.setInitial(false);
        request.setMechanism(sasl.getMechanismName());
        request.setResponse(saslResponse);

        return request;
    }

    /**
     * Initialize the negotiate login request for the last stage of the login process.
     */
    public NegotiateRequest loginNegotiate(ClientTransport xport) {
        NegotiateRequest request = new NegotiateRequest();

        request.setExchangeID();

        // Set the request with the fore channel command sequence and slot table parameters
        if (clientChannel != null) {
            clientChannel.update(request);
        } else {
            SessionClientChannel.initialize(request);
        }

        // Set the request with the back channel expected command sequence
        if (serverChannel != null) {
            serverChannel.update(request);
        }

        // Construct a protocol options proposal based on user specification
        ServiceOptions proposal;

        proposal = xport.getOptions().propose();

        synchronized (this) {
            switch (state) {
            case ACTIVE:
                proposal.override(options.propose());
                break;

            default:
                break;
            }
        }

        request.setParams(proposal.toString());

        return request;
    }

    /**
     * Session login negotiation handler. This is invoked by the transport during stage four of the login process
     * after the negotiate response has been received. In case of login failure, either due to server rejection or
     * protocol violation, throw an exception to cause the login process to be aborted and the underlying transport
     * closed.
     */
    public void loginNegotiate(ClientTransport xport, NegotiateResponse response) {
        ServiceOptions result = ServiceOptions.fromString(response.getParams());

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

                clientChannel = new SessionClientChannel(this, true, foreQueue, response.getExpectedCommandSN(),
                        bandwidthLimit);
                serverChannel = new SessionServerChannel(this, false, backQueue, response.getCommandSN());

                // N1 - At least one transport connection reached the LOGGED_IN state
                setState(ClientSessionState.LOGGED_IN);

                break;

            case LOGGED_IN:
                // Implicit self transition
                break;

            case FAILED:
                // N4 - A session continuation attempt succeeded
                setState(ClientSessionState.LOGGED_IN);
                break;

            case ZOMBIE:
                throw new LoginAbortedException();

            default:
                throw new IllegalStateException("invalid session state " + state);
            }

            // Move the transport from the in login list to the logged in list
            boolean contained = active.contains(xport);
            assert contained;
            boolean removed = inLogin.remove(xport);
            assert removed;
            boolean added = loggedIn.add(xport);
            assert added;
        }

        // Refresh the channels with vital info found in the response
        clientChannel.refresh(response);
        serverChannel.refresh(response);

        // Set the negotiated protocol options for the transport
        xport.getOptions().override(result.getTransportOptions());
    }

    /**
     * Session login completion handler. This is invoked after the transport is ready to start. The transport will
     * be attached to the session channels. Upon server channel attach, the transport is unblocked for reading.
     * Upon client channel attach, a ping is sent to the server to unblock it for back channel.
     */
    public synchronized void loginComplete(ClientTransport xport) {
        logger.infof("%s: login succeeded over %s", this, xport);

        if (!loginFuture.isDone()) {
            // Mark the login process completed with success
            loginFuture.setResult(this);

            // Cancel the login timeout if it has been scheduled
            if (timeoutFuture != null) {
                timeoutFuture.cancel(false);
            }

            // Recover the remaining transports now that the leading login succeeded
            scheduleRecovery();
        }

        // Notify the channels
        clientChannel.notifyAttach(xport);
        serverChannel.notifyAttach(xport);
    }

    /**
     * Session login failure handler. Most of the login failures are irrecoverable and lead directly to session
     * reset with few exceptions. The transport is unconditionally closed after processing the login failure due
     * to the exception thrown.
     */
    public void loginFailed(ClientTransport xport, LoginResponse response) {
        LoginStatus status = response.getStatus();
        NexusLoginException e;

        logger.errorf("%s: login failed over %s due to %s", this, xport, status.getDesc());

        switch (status) {
        case VERSION_UNSUPPORTED:
            e = new ProtocolVersionException();
            break;

        case SERVICE_UNAVAILABLE:
            e = new ServiceUnavailableException();
            break;

        case SESSION_INVALID:
        case SESSION_NONEXISTENT:
            e = new NexusNotFoundException("nexus not found on server " + status.getDesc());
            break;

        case TLS_UNSUPPORTED:
        case TLS_REQUIRED:
            e = new SecurityNegotiationException("tls negotiation failed " + status.getDesc());
            break;

        case SASL_FAILURE:
            e = new AuthFailedException("failed to evaluate sasl response");
            break;

        case PARAMETER_UNSUPPORTED:
            e = new ParameterNegotiationException();
            break;

        case SERVICE_UNREACHABLE:
            xport.setRecoverable(false);
            // FALLTHRU

        case CONNECTION_EXCEEDED:
            throw new NexusLoginException("login failed due to " + status.getDesc());

        default:
            throw new IllegalStateException("unexpected login status " + status);
        }

        // Close the session due to irrecoverable login failures
        close(e);

        throw e;
    }

    /**
     * Session login failure handler. This is used to handle a login exception generated locally on the client. The
     * exception leads to transport close and session reset.
     */
    private void loginFailed(ClientTransport xport, NexusLoginException e) {
        logger.errorf(e, "%s: nexus closing upon login failure over %s", this, xport);

        // Close the session upon login failure
        close(e);

        // Force the transport to close if it is still connected
        if (xport.isConnected()) {
            throw e;
        }
    }

    /**
     * Notify the session the transport has been disconnected.
     */
    private void disconnect(ClientTransport xport) {
        boolean removed = true;
        switch (state) {
        case ACTIVE:
            removed = inLogin.remove(xport);
            assert removed;

            // Reset the tentative session properties set early on during the leading login
            if (handle != null) {
                handle = null;

                actVersion = null;
                maxVersion = null;
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
                setState(ClientSessionState.FAILED);
            }

            // Notify the channels
            clientChannel.notifyDetach(xport);
            serverChannel.notifyDetach(xport);

            break;

        case FAILED:
            removed = inLogin.remove(xport);
            assert removed;
            break;

        case ZOMBIE:
            if (inLogin.remove(xport)) {
                break;
            }

            removed = loggedIn.remove(xport);
            assert removed;

            // Notify the channels
            clientChannel.notifyDetach(xport);
            serverChannel.notifyDetach(xport);

            break;

        default:
            throw new IllegalStateException("invalid session state " + state);
        }
    }

    /**
     * Notify the session the transport has been closed. This notification is delivered after the transport has been
     * fully quiesced. All exchanges associated with the transport have been returned and the underlying channel
     * closed. If the session has not been reset and the transport is recoverable, it shall be set up for recovery
     * at a later time. Otherwise, it is parked on the zombie list before disposal.
     */
    public void notifyClosed(ClientTransport xport) {
        boolean needReset = false;

        logger.infof("%s: %s closed", this, xport);

        synchronized (this) {
            disconnect(xport);

            switch (state) {
            case ACTIVE:
                if (!xport.isRecoverable()) {
                    needReset = withdraw(xport);
                } else {
                    xport = recover(xport);
                    unbound.add(xport);
                }

                // Schedule for recovery if leading login failed
                if (!needReset) {
                    scheduleRecovery();
                }

                break;

            case LOGGED_IN:
            case FAILED:
                if (!xport.isRecoverable()) {
                    needReset = withdraw(xport);
                    break;
                }

                xport = recover(xport);

                if (unbound.isEmpty()) {
                    unbound.add(xport);
                    scheduleRecovery();
                    break;
                }

                // Add the failed transport to the unbound list in order of recovery time
                long now = System.currentTimeMillis();
                long elapsed = xport.getRecoveryDelay(now);

                ListIterator<ClientTransport> iter = unbound.listIterator(unbound.size());

                while (iter.hasPrevious()) {
                    ClientTransport item = iter.previous();

                    if (elapsed >= item.getRecoveryDelay(now)) {
                        iter.next(); // Move the cursor next
                        iter.add(xport);
                        break;
                    }
                }

                /*
                 * Reschedule recovery if the failed transport has the earliest recovery time and the current recovery
                 * task is cancelled before it started.
                 */
                if (!iter.hasPrevious()) {
                    iter.add(xport);

                    if (recoveryFuture.cancel(false)) {
                        scheduleRecovery();
                    }
                }

                break;

            case ZOMBIE:
                if (withdraw(xport)) {
                    initiateShutdown();
                }

                break;

            default:
                throw new IllegalStateException("invalid session state " + state);
            }
        }

        /*
         * When the transport has experienced an irrecoverable failure, it will be retired to the zombie list. When
         * all transports have failed irrecoverably, the session is permanently lost and therefore must be reset.
         */
        if (needReset) {
            loginFailed(xport, new ServiceUnreachableException());
        }
    }

    private ClientTransport recover(ClientTransport xport) {
        ClientTransport next = xport.recover();

        boolean removed = active.remove(xport);
        assert removed;
        boolean added = active.add(next);
        assert added;

        return next;
    }

    /**
     * Withdraw the session transport that has failed due to irrecoverable causes. The most common failure a transport
     * may experience is loss of network connectivity, which is a recoverable failure since the transport will be
     * brought back to life when the network connectivity is restored. In contrast, some failures cannot be fixed
     * through retry. An example of the latter occurs when the service is not configured to be accessible via the
     * network interface associated with the transport.
     */
    private boolean withdraw(ClientTransport xport) {
        // Move the irrecoverable transport to the zombie list
        boolean removed = active.remove(xport);
        assert removed;
        boolean added = zombie.add(xport);
        assert added;

        logger.infof("%s: %s withdrawn", this, xport);

        return active.isEmpty();
    }

    /**
     * Reset the session. All active session states shall be discarded, including the session transports and the
     * outstanding commands. The session shall be removed from the session manager after it has been quiesced.
     * Application that owns the session shall be notified of the session loss. This is invoked when the session
     * encounters irrecoverable failures such as session state timeout on the server.
     */
    private void reset(Throwable t) {
        List<ClientTransport> xports;

        if (t == null) {
            logger.infof("%s: session closed", this);
        } else {
            logger.errorf("%s: session reset due to %s", this, t);
        }

        synchronized (this) {
            // Reset only if it has not been
            if (state == ClientSessionState.ZOMBIE) {
                return;
            }

            // N3, N6, N7
            setState(ClientSessionState.ZOMBIE);

            // Mark the login process failed if it has never completed
            if (!loginFuture.isDone()) {
                if (t == null) {
                    t = new LoginAbortedException();
                }

                loginFuture.setException(t);
            }

            if (timeoutFuture != null) {
                timeoutFuture.cancel(false);
            }

            if (recoveryFuture != null) {
                recoveryFuture.cancel(false);
            }

            // Move all unbound transports to the zombie list
            if (!unbound.isEmpty()) {
                assert active.containsAll(unbound);

                active.removeAll(unbound);
                zombie.addAll(unbound);

                unbound.clear();
            }

            // Remove the session from the client manager
            getClientManager().remove(client, server);

            // Dispose of the session now if it has been quiesced
            if (active.isEmpty()) {
                initiateShutdown();
                return;
            }

            // Get a list of all active session transports
            xports = new ArrayList<ClientTransport>();
            xports.addAll(active);
        }

        // Initiate the close of all active session transports
        for (ClientTransport xport : xports) {
            xport.close();
        }
    }

    /**
     * Dispose of the session. This happens after all transports have been withdrawn either due to irrecoverable
     * transport failures or session reset. The actual disposal is performed in another context to avoid blocking
     * the caller.
     */
    private void initiateShutdown() {
        execute(new Runnable() {

            @Override
            public void run() {
                shutdown();
            }
        });
    }

    private void shutdown() {
        logger.infof("%s: session shutdown", this);

        // Flush pending events and destroy event source
        getEventManager().flush(this, true);

        // Shutdown the client channel
        if (clientChannel != null) {
            clientChannel.shutdown();
        }

        // Shutdown the server channel
        if (serverChannel != null) {
            serverChannel.shutdown();
        }

        getClientManager().dispose(this);

        zombie.clear();

        logger.infof("%s: session disposed", this);

        // Mark the nexus closed after shutdown is complete
        closeFuture.setResult(this);
    }

    /**
     * Recover the session. When a transport fails with a recoverable error, it is placed on the unbound transport
     * list and the parent session is scheduled for recovery if not already. The session recovery manager goes over
     * the list of session in need of recovery and recovers each of them on a periodic basis.
     *
     * If the session hasn't logged in yet, the first transport on the unbound list is chosen for the leading login.
     * There is never more than one leading login on the session to avoid login race that might lead to session state
     * thrashing. Leading login is issued over the available transports in a round-robin manner. In other words, if
     * the leading login fails, the transport over which it is issued is placed at the end of the unbound list and
     * a retry is issued over the next transport.
     *
     * On the other hand, if the session has logged in before, it is safe to recover all failed transports at the
     * same time.
     */
    public void recover() {
        ClientTransport xport;

        logger.debugf("%s: recovery in progress", this);

        synchronized (this) {
            long now = System.currentTimeMillis();

            switch (state) {
            case ACTIVE:
                if (unbound.isEmpty()) {
                    break;
                }

                xport = unbound.get(0);

                if (xport.getRecoveryDelay(now) > 0) {
                    scheduleRecovery();
                    break;
                }

                xport = unbound.remove(0);

                logger.debugf("%s: leading login over %s", this, xport);

                getClientManager().connect(xport);
                inLogin.add(xport);

                break;

            case LOGGED_IN:
            case FAILED:
                Iterator<ClientTransport> iter = unbound.iterator();

                while (iter.hasNext()) {
                    xport = iter.next();

                    if (xport.getRecoveryDelay(now) > 0) {
                        break;
                    }

                    iter.remove();

                    logger.debugf("%s: login over %s", this, xport);

                    getClientManager().connect(xport);
                    inLogin.add(xport);
                }

                /*
                 * Reschedule the session for recovery if there is any unbound transport that needs to be recovered
                 * at a later point in time.
                 */
                scheduleRecovery();

                break;

            default:
                break;
            }
        }
    }

    /**
     * Schedule the session for recovery with the delay set to the recovery time of the first transport on the
     * unbound list which is sorted by the recovery time.
     */
    private void scheduleRecovery() {
        if (unbound.isEmpty() || closeFuture != null) {
            return;
        }

        ClientTransport xport = unbound.get(0);

        long now = System.currentTimeMillis();
        long delay = xport.getRecoveryDelay(now);

        Runnable task = new Runnable() {

            @Override
            public void run() {
                recover();
            }
        };

        recoveryFuture = schedule(task, delay);
    }

    @Override
    public void addListener(NexusListener listener) {
        dispatcher.addListener(listener);
    }

    @Override
    public void removeListener(NexusListener listener) {
        dispatcher.removeListener(listener);
    }

    @Override
    public void notify(NexusListener listener) {
        // Listener registration is ignored due to session event ordering constraints
    }

    @Override
    public void notify(NexusListener listener, Event event) {
        ((NexusEvent) event).notify(listener);
    }

    @Override
    public boolean isTerminal() {
        return state == ClientSessionState.ZOMBIE;
    }

    @Override
    public EventSource getEventSource() {
        return this;
    }

    private class StateChangeEvent extends NexusEvent {

        private final ClientSessionState oldState;
        private final ClientSessionState newState;

        public StateChangeEvent(ClientSessionState oldState, ClientSessionState newState) {
            super(ClientSession.this);

            this.oldState = oldState;
            this.newState = newState;
        }

        @Override
        public void notify(NexusListener listener) {
            notifyListener(listener, oldState, newState);
        }

        @Override
        public void run() {
            dispatcher.dispatch(this);
        }
    }

    /**
     * Notify the session listener of the state change event.
     */
    private void notifyListener(NexusListener listener, ClientSessionState oldState, ClientSessionState newState) {
        switch (newState) {
        case LOGGED_IN:
            if (oldState == ClientSessionState.ACTIVE) {
                listener.nexusEstablished(this);
            } else if (oldState == ClientSessionState.FAILED) {
                listener.nexusRestored(this);
            }

            break;

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
    public String toString() {
        return "nexus-c:" + client + "-" + server;
    }
}
