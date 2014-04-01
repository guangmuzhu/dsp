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

import com.delphix.session.impl.common.ProtocolViolationException;
import com.delphix.session.impl.common.SessionNexus;
import com.delphix.session.impl.common.SessionTransport;
import com.delphix.session.impl.common.TransportResetException;
import com.delphix.session.impl.frame.*;
import com.delphix.session.net.NetServerConfig;
import com.delphix.session.sasl.SaslServerConfig;
import com.delphix.session.sasl.ServerSaslMechanism;
import com.delphix.session.service.ProtocolHandler;
import com.delphix.session.service.ServerConfig;
import com.delphix.session.service.ServiceTerminus;
import com.delphix.session.ssl.SSLServerContext;
import com.delphix.session.ssl.TransportSecurityLevel;
import com.delphix.session.util.ProtocolVersion;
import org.jboss.netty.channel.socket.SocketChannelConfig;

import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.delphix.session.service.ServiceOption.SOCKET_RECEIVE_BUFFER;
import static com.delphix.session.service.ServiceOption.SOCKET_SEND_BUFFER;

/**
 * This class represents an individual transport connection between client and server for a given session. It
 * implements the server transport state machine required to create a new transport connection, authenticate,
 * negotiate, and login. It interfaces with the socket and the affiliated channel pipeline from below and the session
 * from above.
 */
public class ServerTransport extends SessionTransport {

    private final ServerManagerImpl manager; // Server manager

    private ServerImpl server; // Server instance
    private ServerSession session; // Parent session

    private SaslServer sasl; // SASL context for login only
    private TransportSecurityLevel tls; // TLS level negotiated

    private ServerTransportState state; // Session transport state

    private boolean ready; // Client callback ready

    /**
     * The transport is created when the underlying channel is opened.
     */
    public ServerTransport(ServerManagerImpl manager) {
        super(manager);

        this.manager = manager;

        // Keep track of the transport in the session manager until it is closed
        manager.addTransport(this);

        // Initialize the transport state
        setState(ServerTransportState.FREE);
    }

    public ServerManagerImpl getManager() {
        return manager;
    }

    public ServerImpl getServer() {
        return server;
    }

    public ServerSession getSession() {
        return session;
    }

    public ServerTransportState getState() {
        return state;
    }

    private void setState(ServerTransportState newState) {
        ServerTransportState oldState = state;

        ServerTransportState.validate(oldState, newState);
        state = newState;

        logger.debugf("%s: state transition %s -> %s", this, oldState, newState);
    }

    @Override
    public boolean isClient() {
        return false;
    }

    @Override
    public SessionNexus getNexus() {
        return session;
    }

    public TransportSecurityLevel getTlsLevel() {
        return tls;
    }

    public boolean isClientReady() {
        return ready;
    }

    public void setClientReady() {
        assert !ready;
        ready = true;
    }

    /**
     * Notify the transport the underlying channel has been connected.
     */
    @Override
    public void notifyConnected() {
        // T3 - Received a valid transport connection request that establishes the transport connection
        setState(ServerTransportState.XPT_UP);
    }

    /**
     * Notify the transport the underlying channel has been closed.
     *
     * The transport may be disconnected for a variety of reasons, whether initiated by us, by the peer, or due
     * to unintended network failures. Regardless of the cause of the disconnect, it is all coming through here
     * and to the session if necessary.
     */
    @Override
    public void notifyClosed() {
        // Complete outstanding exchanges
        if (!shutdown()) {
            return;
        }

        // T6, T7, T8, T13
        setState(ServerTransportState.FREE);

        // Notify the session if the transport has done a successful login connect
        if (session != null) {
            session.notifyClosed(this);
        }

        // Remove the transport from the session manager now that it is closed
        manager.removeTransport(this);
    }

    /**
     * Session transport connect handler. This is stage one of the login process. It involves a single exchange
     * between the client and the server. The information exchanged at this stage includes
     *
     *   - the protocol version range
     *   - the client and service terminus
     *   - SSL support
     *   - supported SASL mechanisms
     *   - optionally a session handle
     *
     * We should avoid operating on the session in a harmful way in this stage just in case the client turns out
     * to be malicious.
     */
    public ConnectResponse connect(ConnectRequest request) {
        ConnectResponse response = new ConnectResponse();

        response.setExchangeID(request.getExchangeID());

        logger.debugf("%s: %s", this, request);

        // T4 - Initial Login request was received
        setState(ServerTransportState.IN_LOGIN);

        /*
         * Verify the client specified protocol version range is valid and overlaps with the version range supported
         * by the session manager.
         */
        if (!validateVersion(request, response)) {
            return response;
        }

        // Locate the session server by the client specified server terminus
        server = locateServer(request, response);

        if (server == null) {
            return response;
        }

        // Verify the local network address to which the transport is bound is accessible by the service
        if (!validateNetwork(request, response)) {
            return response;
        }

        // Verify the client specified SSL setting is compatible with the service configuration
        if (!validateSSL(request, response)) {
            return response;
        }

        // Construct the SASL mechanisms
        if (!setSaslMechanisms(response)) {
            return response;
        }

        // Verify the client specified session handle or in case of null if a session exists bound to the client
        ServerSession session = locateSession(request, response);

        if (session == null) {
            return response;
        }

        // Notify the session of the login request over this transport
        if (!session.loginConnect(this, request, response)) {
            return response;
        }

        // Set the session after a successful login connect
        this.session = session;

        logger.debugf("%s: %s", this, response);

        return response;
    }

    /**
     * Verify the client specified protocol version range is valid. Otherwise, close the connection immediately
     * without response.
     *
     * Verify the client specified protocol version range has overlap with the version range supported by the
     * session manager. Otherwise, respond with the supported version range before close the connection.
     *
     * Select the maximum version supported by both the client and the server to be the active version in use and
     * return the server supported maximum version at the same time.
     */
    private boolean validateVersion(ConnectRequest request, ConnectResponse response) {
        ProtocolVersion minClient = request.getMinVersion();
        ProtocolVersion maxClient = request.getMaxVersion();

        if (minClient.greaterThan(maxClient)) {
            throw new ProtocolViolationException("invalid version range " + minClient + " to " + maxClient);
        }

        ProtocolVersion minServer = manager.getMinVersion();
        ProtocolVersion maxServer = manager.getMaxVersion();

        if (maxClient.lessThan(minServer) || minClient.greaterThan(maxServer)) {
            response.setStatus(LoginStatus.VERSION_UNSUPPORTED);

            response.setActVersion(minServer);
            response.setMaxVersion(maxServer);

            return false;
        }

        if (maxClient.greaterThan(maxServer)) {
            response.setActVersion(maxServer);
        } else {
            response.setActVersion(maxClient);
        }

        response.setMaxVersion(maxServer);

        return true;
    }

    /**
     * Verify if a service is registered by the client specified service terminus. Return the server if one is found.
     * Otherwise, set the response status to service unavailable and return null.
     */
    private ServerImpl locateServer(ConnectRequest request, ConnectResponse response) {
        ServerImpl server = (ServerImpl) manager.locate(request.getServer());

        if (server == null) {
            response.setStatus(LoginStatus.SERVICE_UNAVAILABLE);
            return null;
        }

        setOptions(server.getConfig().getOptions());

        return server;
    }

    /**
     * Verify the local address to which the transport is bound is included in the service network configuration.
     * Otherwise, respond with network inaccessible before close the connection.
     */
    private boolean validateNetwork(ConnectRequest request, ConnectResponse response) {
        InetSocketAddress addr = (InetSocketAddress) channel.getLocalAddress();

        if (addr == null) {
            throw new TransportResetException("local address unavailable");
        }

        ServerConfig config = server.getConfig();
        NetServerConfig net = config.getNetConfig();

        if (net != null && !net.contains(addr.getAddress())) {
            response.setStatus(LoginStatus.SERVICE_UNREACHABLE);
            return false;
        }

        return true;
    }

    /**
     * Verify the client specified TLS setting is compatible with the service configuration. If not, respond with
     * the appropriate login status before close the connection. Otherwise, choose the TLS level to be used for the
     * transport.
     *
     * Here is how the resulting transport security level is determined from the client and server offers.
     *
     *    server offer    client offer     result
     *    ------------    ------------     ------------
     *    !supported      !optional        failure
     *    !supported       optional        !supported
     *     optional       !optional        client offer
     *     optional       optional         !supported
     *    !optional       !supported       failure
     *    !optional        supported       max(client offer, server offer)
     *
     * The result TLS level is either null (indicating !supported) or one of AUTHENTICATION and ENCRYPTION.
     */
    private boolean validateSSL(ConnectRequest request, ConnectResponse response) {
        TransportSecurityLevel newLevel = request.getTlsLevel();

        ServerConfig config = server.getConfig();
        SSLServerContext ssl = config.getSslContext();

        if (ssl != null) {
            TransportSecurityLevel tlsLevel = ssl.getTlsLevel();

            if (tlsLevel != TransportSecurityLevel.OPTIONAL) {
                if (newLevel == null) {
                    response.setStatus(LoginStatus.TLS_REQUIRED);
                    return false;
                } else if (newLevel.compareTo(tlsLevel) < 0) {
                    newLevel = tlsLevel;
                }
            } else if (newLevel == TransportSecurityLevel.OPTIONAL) {
                newLevel = null;
            }
        } else if (newLevel != null) {
            if (newLevel != TransportSecurityLevel.OPTIONAL) {
                response.setStatus(LoginStatus.TLS_UNSUPPORTED);
                return false;
            } else {
                newLevel = null;
            }
        }

        response.setTlsLevel(newLevel);

        tls = newLevel;

        return true;
    }

    /**
     * Construct the SASL mechanism list and include it in the response.
     */
    private boolean setSaslMechanisms(ConnectResponse response) {
        ServerConfig config = server.getConfig();
        SaslServerConfig sasl = config.getSaslConfig();

        List<String> mechanisms = new ArrayList<String>();

        for (ServerSaslMechanism mechanism : sasl.getMechanisms()) {
            mechanisms.add(mechanism.getMechanism());
        }

        if (mechanisms.isEmpty()) {
            response.setStatus(LoginStatus.SASL_FAILURE);
            return false;
        }

        response.setSaslMechanisms(mechanisms);

        return true;
    }

    /**
     * Verify the session handle if one is specified by the client. The session handle must refer to an existing
     * session that belongs to the same client. If not, respond with the appropriate login status before close the
     * connection. The protocol version chosen by the session should be the same as the active version chosen for
     * this login assuming the client uses the same version range for all the logins to this session. Otherwise,
     * the login shall be changed to use the session's active version if the latter is compatible with the version
     * range in the connect request. If not, close the connection without response. If a session handle is not
     * specified, a new session shall be instantiated.
     */
    private ServerSession locateSession(ConnectRequest request, ConnectResponse response) {
        SessionHandle handle = request.getSessionHandle();
        ServiceTerminus client = request.getClient();
        ProtocolVersion version = response.getActVersion();

        ServerSession session;

        if (handle != null) {
            session = server.locate(handle);

            if (session == null) {
                response.setStatus(LoginStatus.SESSION_NONEXISTENT);
                return null;
            } else if (!session.getClientTerminus().equals(client)) {
                response.setStatus(LoginStatus.SESSION_INVALID);
                return null;
            }

            ProtocolVersion active = session.getActVersion();

            if (!version.equals(active)) {
                logger.warnf("%s: active version %s does not match login %s", this, active, version);

                if (active.greaterThan(request.getMaxVersion()) || active.lessThan(request.getMinVersion())) {
                    throw new ProtocolViolationException("active version " + active + " out of range " +
                            request.getMinVersion() + " to " + request.getMaxVersion());
                }

                response.setActVersion(active);
            }
        } else {
            Collection<? extends ProtocolHandler<?>> handlers = server.getProtocolHandlerFactory().getHandlers(client);
            session = new ServerSession(version, client, server, handlers);
        }

        // Set the session handle
        response.setSessionHandle(session.getHandle());

        return session;
    }

    /**
     * Session transport login authentication handler. Authentication is stage three of the login process. It may
     * involve multiple round trips between the client and the server, depending on the SASL mechanism agreed upon.
     * Authentication is handled entirely in the transport layer without involving the session.
     */
    public AuthenticateResponse authenticate(AuthenticateRequest request) {
        AuthenticateResponse response = new AuthenticateResponse();

        response.setExchangeID(request.getExchangeID());

        logger.debugf("%s: %s", this, request);

        byte[] saslResponse = request.getResponse();

        // Validate the SASL authenticate request and construct the SASL context if necessary
        if (!validateSasl(request, response)) {
            return response;
        }

        // Evaluate the SASL response and prepare the SASL challenge for the next round
        byte[] saslChallenge;

        try {
            saslChallenge = sasl.evaluateResponse(saslResponse);

            response.setChallenge(saslChallenge);
            response.setComplete(sasl.isComplete());

            if (sasl.isComplete()) {
                String user = sasl.getAuthorizationID();
                logger.infof("%s: sasl authentication completed as \"%s\"", this, user);

                // Stash away the user name for authorization purposes
                session.setUserName(user);

                sasl.dispose();
                sasl = null;
            }
        } catch (SaslException e) {
            logger.errorf(e, "failed to evaluate sasl response");
            response.setStatus(LoginStatus.SASL_FAILURE);
            return response;
        }

        // Set the response status to success
        response.setStatus(LoginStatus.SUCCESS);

        logger.debugf("%s: %s", this, response);

        return response;
    }

    /**
     * Verify the sanity of the authenticate request. In case of protocol violation, close the connection immediately
     * without response. Otherwise, construct the SASL server authentication context from the client specified SASL
     * mechanism name if this is the initial request.
     */
    private boolean validateSasl(AuthenticateRequest request, AuthenticateResponse response) {
        String mechanism = request.getMechanism();

        if (request.getResponse() == null) {
            logger.errorf("response expected for sasl mechanism %s", mechanism);
            response.setStatus(LoginStatus.SASL_FAILURE);
            return false;
        }

        if (sasl != null) {
            if (request.isInitial()) {
                throw new ProtocolViolationException("unexpected initial authenticate request");
            } else if (!sasl.getMechanismName().equals(mechanism)) {
                throw new ProtocolViolationException("invalid sasl mechanism name " + mechanism +
                        " expecting " + sasl.getMechanismName());
            }

            return true;
        }

        if (!request.isInitial()) {
            throw new ProtocolViolationException("initial authenticate request expected");
        }

        ServerConfig config = server.getConfig();

        try {
            sasl = config.getSaslConfig().create(mechanism);
        } catch (SaslException e) {
            logger.errorf(e, "failed to create server context for sasl mechanism %s", mechanism);
        }

        if (sasl == null) {
            logger.errorf("server context required for sasl mechanism %s", mechanism);
            response.setStatus(LoginStatus.SASL_FAILURE);
            return false;
        }

        return true;
    }

    /**
     * Session transport login negotiation handler. Negotiation is stage four, the last stage, of the login process.
     * Currently, it involves a single exchange between the client and the server. At this stage, the client and the
     * server have mutually authenticated and the transport is optionally secured. Hence, it is safe now to bring up
     * or update the session infrastructure, such as session channels and session state. The bulk of the negotiation
     * is handled in the session.
     */
    public NegotiateResponse negotiate(NegotiateRequest request) {
        NegotiateResponse response = new NegotiateResponse();

        response.setExchangeID(request.getExchangeID());

        logger.debugf("%s: %s", this, request);

        /*
         * T5 - The final Login request to conclude the Login Phase was received, thus prompting the server
         * to send the final Login response with a success status.
         */
        setState(ServerTransportState.LOGGED_IN);

        // Notify the session of login completion for this transport
        if (!session.loginNegotiate(this, request, response)) {
            return response;
        }

        SocketChannelConfig config = (SocketChannelConfig) channel.getConfig();

        config.setSendBufferSize(options.getOption(SOCKET_SEND_BUFFER));
        config.setReceiveBufferSize(options.getOption(SOCKET_RECEIVE_BUFFER));

        logger.infof("%s: socket send buffer size: %s (adjusted)", this, config.getSendBufferSize());
        logger.infof("%s: socket receive buffer size: %s (adjusted)", this, config.getReceiveBufferSize());

        logger.debugf("%s: %s", this, response);

        return response;
    }

    /**
     * Start the transport for normal operation. Server transport is started in two steps following successful login.
     * First of all, it is attached to the server channel after the negotiate response is sent and channel pipeline
     * reconfigured. Next, it is attached to the client channel after it receives the ping request from the client.
     */
    public void start(boolean client) {
        if (client) {
            session.enableClient(this);
        } else {
            session.loginComplete(this);
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("xport-s:");

        SocketAddress addr;

        addr = getLocalAddress();
        builder.append(addr != null ? addr : "<>");

        builder.append("-");

        addr = getRemoteAddress();
        builder.append(addr != null ? addr : "<>");

        return builder.toString();
    }
}
