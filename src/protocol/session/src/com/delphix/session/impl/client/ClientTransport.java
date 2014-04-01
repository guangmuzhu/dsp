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

import com.delphix.session.impl.common.ProtocolViolationException;
import com.delphix.session.impl.common.SessionNexus;
import com.delphix.session.impl.common.SessionTransport;
import com.delphix.session.impl.frame.*;
import com.delphix.session.service.TransportAddress;
import com.delphix.session.ssl.TransportSecurityLevel;
import org.jboss.netty.channel.Channel;

import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

import java.net.SocketAddress;

import static com.delphix.session.service.ServiceOption.RECOVERY_INTERVAL;
import static com.delphix.session.service.ServiceOption.RECOVERY_TIMEOUT;

/**
 * This class represents an individual transport connection between client and server for a given session. It
 * implements the client transport state machine required to create a new transport connection, authenticate,
 * negotiate, and login. It interfaces with the socket and the affiliated channel pipeline from below and the session
 * from above.
 */
public class ClientTransport extends SessionTransport {

    private final TransportAddress address; // Transport address
    private final ClientSession session; // Parent session

    private LoginRequest login; // Outstanding login request
    private SaslClient sasl; // SASL client context for login only
    private TransportSecurityLevel tls; // TLS level negotiated

    private boolean recoverable; // Recoverability indicator

    private final ClientTransportStats stats; // Transport stats

    private ClientTransportState state; // Session transport state

    public ClientTransport(TransportAddress address, ClientSession session) {
        this(address, session, null);
    }

    private ClientTransport(TransportAddress address, ClientSession session, ClientTransportStats stats) {
        super(session.getManager());

        this.address = address;
        this.session = session;

        if (stats == null) {
            this.stats = new ClientTransportStats();
        } else {
            this.stats = new ClientTransportStats(stats);
        }

        setRecoverable(true);

        setOptions(session.getConfig().getOptions());

        // Initialize the transport state
        setState(ClientTransportState.FREE);
    }

    public ClientSession getSession() {
        return session;
    }

    public ClientTransportState getState() {
        return state;
    }

    private void setState(ClientTransportState newState) {
        ClientTransportState oldState = state;

        ClientTransportState.validate(oldState, newState);
        state = newState;

        logger.debugf("%s: state transition %s -> %s", this, oldState, newState);
    }

    public ClientTransportStats getStats() {
        return stats;
    }

    public boolean isRecoverable() {
        return recoverable;
    }

    public void setRecoverable(boolean recoverable) {
        this.recoverable = recoverable;
    }

    /**
     * There may be lingering commands still bound to this transport in various stages of command processing. To make
     * sure transition is clean, we would like to fail all commands still referencing the transport before attempting
     * recovery. Instead of trying to synchronize with the data path, which can get rather hairy, a copy is made from
     * the failed transport and used for recovery going forward, similar to the idea of a generation count.
     */
    public ClientTransport recover() {
        if (!isRecoverable()) {
            return null;
        }

        return new ClientTransport(address, session, stats);
    }

    /**
     * Get the elapsed time from now until the next recovery point. Recovery schedule follows an exponential back off
     * up to the maximum recovery duration.
     */
    public long getRecoveryDelay(long now) {
        long next = stats.getLastFailureTime();

        if (next > 0) {
            int interval = options.getOption(RECOVERY_INTERVAL);
            int timeout = options.getOption(RECOVERY_TIMEOUT);

            next += Math.min((1 << (stats.getNumFailures() - 1)) * interval, timeout);
        }

        if (next > now) {
            return next - now;
        } else {
            return 0;
        }
    }

    @Override
    public SocketAddress getLocalAddress() {
        SocketAddress localAddress = super.getLocalAddress();

        if (localAddress == null) {
            localAddress = address.getLocalAddress();
        }

        return localAddress;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return address.getRemoteAddress();
    }

    @Override
    public boolean isClient() {
        return true;
    }

    @Override
    public SessionNexus getNexus() {
        return session;
    }

    public SaslClient getSaslClient() {
        return sasl;
    }

    public TransportSecurityLevel getTlsLevel() {
        return tls;
    }

    /**
     * Notify the transport the underlying channel has been opened.
     *
     * The channel shall be bound to the transport and remain so until it is closed.
     */
    @Override
    public void notifyOpened(Channel channel) {
        // T1 - Transport connect request was made (e.g., TCP SYN sent)
        setState(ClientTransportState.XPT_WAIT);

        // Bind the channel to the transport
        super.notifyOpened(channel);
    }

    /**
     * Notify the transport the underlying channel has been connected.
     *
     * Start the session login process by initiating the connect login phase.
     */
    @Override
    public void notifyConnected() {
        // T4 - Transport connection established, thus prompting the session to start the Login
        setState(ClientTransportState.IN_LOGIN);

        // Start the login process with the connect phase
        ConnectRequest request = session.loginConnect(this);

        logger.debugf("%s: %s", this, request);

        sendLogin(request);
    }

    /**
     * Notify the transport the underlying channel has been closed.
     *
     * Schedule the transport for recovery.
     */
    @Override
    public void notifyClosed() {
        // Complete outstanding exchanges
        if (!shutdown()) {
            return;
        }

        // T7, T8, T13
        setState(ClientTransportState.FREE);

        // Reset the login specific fields
        resetLogin();

        // Record the transport failure
        stats.transportFailed(this);

        // Notify the session of the transport close
        session.notifyClosed(this);
    }

    /**
     * Session transport connect handler. It is invoked from within the channel handler to process the connect
     * response from the server.
     */
    public void connect(ConnectResponse response) {
        logger.debugf("%s: %s", this, response);

        // Verify the login response
        validateLogin(login, response);

        tls = response.getTlsLevel();

        // Process the connect response
        session.loginConnect(this, response);
    }

    /**
     * Initiate the authentication login phase. It is invoked from within the channel handler to initiate stage
     * three of the login process after connect or TLS handshake is completed successfully.
     */
    public void authenticate() {
        // Initialize the SASL client context
        sasl = session.createSaslClient(this);

        // Process the initial authenticate request
        AuthenticateRequest request = session.loginAuthenticate(this);

        logger.debugf("%s: %s, sasl negotiation completed %b", this, request, sasl.isComplete());

        // Send the login request to the server
        sendLogin(request);
    }

    /**
     * Session transport authenticate handler. It is invoked from within the channel handler to process the
     * authenticate response from the server. Depending on the SASL state, it may generate more authenticate
     * requests.
     */
    public void authenticate(AuthenticateResponse response) {
        logger.debugf("%s: %s, sasl client completed %b", this, response, sasl.isComplete());

        // Verify the login response
        validateLogin(login, response);

        // Continue the login authenticate process
        AuthenticateRequest request = session.loginAuthenticate(this, response);

        if (request == null) {
            return;
        }

        logger.debugf("%s: %s, sasl negotiation completed %b", this, request, sasl.isComplete());

        // Send the login request to the server
        sendLogin(request);
    }

    /**
     * Initiate the parameter negotiation login phase. This is invoked from within the channel handler after the
     * transport has been authenticated.
     */
    public void negotiate() {
        NegotiateRequest request = session.loginNegotiate(this);

        logger.debugf("%s: %s", this, request);

        sendLogin(request);
    }

    /**
     * Session transport negotiate handler. This is invoked from within the channel handler to process the negotiate
     * response from the server. If the parameter negotiation is successful, the transport is finally logged in and
     * the session is notified.
     */
    public void negotiate(NegotiateResponse response) {
        logger.debugf("%s: %s", this, response);

        // Verify the login response
        validateLogin(login, response);

        // Notify the session of negotiation response
        session.loginNegotiate(this, response);

        // Record the transport logged in
        stats.transportLoggedIn(this);

        // Reset the outstanding login request
        resetLogin();

        // T5 - The final Login Response with a success status was received
        setState(ClientTransportState.LOGGED_IN);
    }

    /**
     * Start the transport. This is invoked from within the channel handler after the channel pipeline has been
     * properly configured and is ready to start the operate phase. The channel is blocked for read and the server
     * is still waiting for its cue to start.
     */
    public void start() {
        session.loginComplete(this);
    }

    /**
     * Verify the login request and response are part of the same exchange and the login status in the login response
     * indicates OK to continue. Otherwise, throw an exception to abort the login attempt and close the connection.
     */
    private void validateLogin(LoginRequest request, LoginResponse response) {
        // Verify the request and response are part of the same exchange
        validateExchange(request, response);

        // Verify the login response indicates success or to continue
        LoginStatus status = response.getStatus();

        if (status != LoginStatus.SUCCESS) {
            session.loginFailed(this, response);
        }
    }

    /**
     * Verify that the request and response share the same exchange ID and therefore constitute part of the same
     * exchange between the client and the server. If not, an exception is thrown.
     */
    private void validateExchange(RequestFrame request, ResponseFrame response) {
        ExchangeID requestXid = request.getExchangeID();
        ExchangeID responseXid = response.getExchangeID();

        if (!requestXid.equals(responseXid)) {
            throw new ProtocolViolationException("exchange ID " + responseXid + " expected " + requestXid);
        }
    }

    /**
     * Send the given login request through the channel to the server. Before writing it out, set the one and only
     * outstanding login request against which the response will be matched.
     */
    private void sendLogin(LoginRequest request) {
        login = request;
        channel.write(request);
    }

    /**
     * Reset the login specific fields. This is invoked when the transport leaves the login process either due to
     * successful completion or abnormal termination.
     */
    private void resetLogin() {
        // Reset the login request if any
        if (login != null) {
            login = null;
        }

        // Dispose of the SASL client if any
        if (sasl != null) {
            try {
                sasl.dispose();
            } catch (SaslException e) {
                logger.warnf("%s: failed to dispose of sasl client due to %s", this, e.getMessage());
            }

            sasl = null;
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("xport-c:");

        SocketAddress addr;

        addr = getLocalAddress();
        builder.append(addr != null ? addr : "<>");

        builder.append("-");

        addr = getRemoteAddress();
        builder.append(addr != null ? addr : "<>");

        return builder.toString();
    }
}
