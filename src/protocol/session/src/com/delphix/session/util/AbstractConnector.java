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

package com.delphix.session.util;

import com.delphix.appliance.server.exception.DelphixInterruptedException;
import com.delphix.appliance.server.util.ExceptionUtil;
import com.delphix.session.sasl.AnonymousClient;
import com.delphix.session.sasl.ClientSaslMechanism;
import com.delphix.session.service.*;
import com.delphix.session.ssl.SSLClientContext;
import com.delphix.session.ssl.SSLClientParams;
import com.delphix.session.ssl.SSLContextFactory;
import com.delphix.session.ssl.TransportSecurityLevel;

import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import static com.delphix.session.service.ServiceOption.*;
import static com.delphix.session.service.ServiceProtocol.PORT;

/**
 * This class implements the client side of an abstract session service. It enables client session management, such
 * as creation and registration with the core DSP framework, connection, and closing, service options applicable to
 * client side only, as well as client side security configuration interfaces including authentication and encryption.
 * It allows protocol clients to be created to multiple instances of the service running on different hosts.
 *
 * To use the connector, follow the steps below.
 *
 *   - create a client nexus with host, protocol handler, username, and password
 *   - instantiate a protocol client implementation
 *   - connect to the server
 *
 * For examples, please refer to the com.delphix.session.module.remote module for how to instantiate a concrete
 * connector.
 */
public abstract class AbstractConnector extends AbstractService {

    // Protocol client terminus
    protected ServiceTerminus terminus;
    protected ClientConfigurator configurator;

    // Protocol client manager
    protected ClientManager manager;

    public AbstractConnector(ServiceType type, ServiceCodec codec) {
        this(type, codec, null, null);
    }

    public <E extends Enum<E> & ExchangeType> AbstractConnector(ServiceType type, Class<E> clazz) {
        this(type, clazz, null, null);
    }

    public AbstractConnector(ServiceType type, ServiceCodec codec, UUID client, String name) {
        super(type, codec);

        configurator = ClientConfigurator.getDefault();

        if (client != null) {
            terminus = new ServiceUUID(client, false, name);
        }
    }

    public <E extends Enum<E> & ExchangeType> AbstractConnector(ServiceType type, Class<E> clazz, UUID client, String name) {
        super(type, clazz);

        configurator = ClientConfigurator.getDefault();

        if (client != null) {
            terminus = new ServiceUUID(client, false, name);
        }
    }

    @Override
    public void start() {
        super.start();

        // Use the system-wide client terminus if it's not set
        if (terminus == null) {
            terminus = manager.getTerminus();
        }
    }

    /**
     * Create a single-connection client nexus with default configuration.
     */
    public ClientNexus create(String addr, String user, String password, Collection<ProtocolHandler<?>> handlers) {
        return create(new String[] { addr }, user, password, handlers);
    }

    /**
     * Create a multi-connection client nexus with default configuration.
     */
    public ClientNexus create(String[] addrs, String user, String password,
            Collection<ProtocolHandler<?>> handlers) {
        return create(addrs, user, password, configurator, handlers);
    }

    /**
     * Create a multi-connection client nexus with default configuration.
     */
    public ClientNexus create(String[] addrs, String user, String password, Collection<ProtocolHandler<?>> handlers,
            NexusListener listener) {
        return create(addrs, user, password, configurator, handlers, listener);
    }

    /**
     * Create a multi-connection client nexus with custom configuration.
     */
    public ClientNexus create(String[] addrs, String user, String password, ClientConfigurator configurator,
            Collection<ProtocolHandler<?>> handlers) {
        return create(addrs, PORT, user, password, configurator, terminus, handlers, getNexusListener());
    }

    /**
     * Create a multi-connection client nexus with custom configuration.
     */
    public ClientNexus create(String[] addrs, String user, String password, ClientConfigurator configurator,
            Collection<ProtocolHandler<?>> handlers, NexusListener listener) {
        return create(addrs, PORT, user, password, configurator, terminus, handlers, listener);
    }

    /**
     * Create a single-connection client nexus with user specified destination port and custom configuration.
     */
    public ClientNexus create(String addr, int port, String user, String password, ClientConfigurator configurator,
            ServiceTerminus terminus, Collection<ProtocolHandler<?>> handlers, NexusListener listener) {
        return create(new String[] { addr }, port, user, password, configurator, terminus, handlers, listener);
    }

    /**
     * Create a multi-connection client nexus with user specified destination port and custom configuration.
     */
    public ClientNexus create(String[] addrs, int port, String user, String password, ClientConfigurator configurator,
            ServiceTerminus terminus, Collection<ProtocolHandler<?>> handlers, NexusListener listener) {
        TransportAddress[] addresses = new TransportAddress[addrs.length];

        for (int i = 0; i < addrs.length; i++) {
            try {
                addresses[i] = new TransportAddress(addrs[i], port);
            } catch (UnknownHostException e) {
                logger.errorf(e, "failed to resolve address %s", addrs[i]);
                throw ExceptionUtil.getDelphixException(e);
            }
        }

        return create(addresses, user, password, configurator, terminus, handlers, listener);
    }

    /**
     * Create a multi-connection client nexus with user specified transport addresses and custom configuration.
     */
    public ClientNexus create(TransportAddress[] addrs, String user, String password,
            ClientConfigurator configurator, ServiceTerminus terminus, Collection<ProtocolHandler<?>> handlers,
            NexusListener listener) {
        ClientConfig config = new ClientConfig(terminus, type.getServiceName(), this, handlers);

        // Configure SASL
        config.setSaslMechanism(getSaslConfig(user, password));
        config.setServerHost("server.domain");

        // Configure SSL
        SSLClientParams params = getSSLParams();

        if (params != null) {
            if (configurator.isEncryptionEnabled()) {
                params.setTlsLevel(TransportSecurityLevel.ENCRYPTION);
            } else {
                params.setTlsLevel(TransportSecurityLevel.AUTHENTICATION);
            }

            try {
                SSLClientContext ssl = SSLContextFactory.getClientContext(params);
                config.setSslContext(ssl);
            } catch (Throwable t) {
                logger.errorf(t, "failed to initialize SSL context");
                throw ExceptionUtil.getDelphixException(t);
            }
        }

        // Initialize transport addresses
        config.setAddresses(Arrays.asList(addrs));

        // Configure custom command executor
        if (protocolExecutor != null) {
            config.setExecutor(protocolExecutor);
        }

        ServiceOptions proposal = config.getOptions();

        proposal.setOption(SYNC_DISPATCH, configurator.getCommandSyncDispatch());
        proposal.setOption(LOGOUT_TIMEOUT, configurator.getLogoutTimeoutSeconds());
        proposal.setOption(MIN_KEEPALIVE_TIME, configurator.getAPDTimeoutSeconds());

        proposal.setOption(RECOVERY_INTERVAL, configurator.getRecoveryInterval());
        proposal.setOption(RECOVERY_TIMEOUT, configurator.getRecoveryTimeout());

        proposal.setOption(HEADER_DIGEST, configurator.getHeaderDigestMethods());
        proposal.setOption(FRAME_DIGEST, configurator.getFrameDigestMethods());
        proposal.setOption(PAYLOAD_DIGEST, configurator.getPayloadDigestMethods());
        proposal.setOption(DIGEST_DATA, configurator.getPayloadDigestData());

        // Configurable options
        List<String> compress;

        if (configurator.isCompressionEnabled()) {
            compress = configurator.getPayloadCompressMethods();
        } else {
            compress = configurator.getPayloadCompressNone();
        }

        proposal.setOption(PAYLOAD_COMPRESS, compress);

        config.setLoginTimeout(configurator.getLoginTimeoutSeconds());
        proposal.setOption(FORE_QUEUE_DEPTH, configurator.getForeCommandQueueDepth());
        proposal.setOption(FORE_MAX_REQUEST, configurator.getForeMaxRequestSize());
        proposal.setOption(BACK_QUEUE_DEPTH, configurator.getBackCommandQueueDepth());
        proposal.setOption(BACK_MAX_REQUEST, configurator.getBackMaxRequestSize());
        proposal.setOption(SOCKET_SEND_BUFFER, configurator.getSendBufferSize());
        proposal.setOption(SOCKET_RECEIVE_BUFFER, configurator.getReceiveBufferSize());
        proposal.setOption(BANDWIDTH_LIMIT, configurator.getBandwidthLimit());

        // Create the nexus
        ClientNexus nexus = manager.create(config);

        // Register session listener
        if (listener != null) {
            nexus.addListener(listener);
        }

        return nexus;
    }

    public void connect(ClientNexus nexus) {
        // Initiate login and wait til done
        LoginFuture future = nexus.login();

        try {
            future.await();
        } catch (ExecutionException e) {
            throw ExceptionUtil.getDelphixException(ExceptionUtil.unwrap(e));
        } catch (CancellationException e) {
            throw new DelphixInterruptedException();
        }

        logger.infof("%s: nexus options - %s", nexus, nexus.getOptions());

        Collection<ServiceTransport> xports = nexus.getTransports();

        for (ServiceTransport xport : xports) {
            logger.infof("%s: transport options - %s", xport, xport.getOptions());
        }
    }

    public static void close(ClientNexus nexus) {
        CloseFuture future = nexus.close();

        try {
            future.await();
        } catch (ExecutionException e) {
            throw ExceptionUtil.getDelphixException(ExceptionUtil.unwrap(e));
        } catch (CancellationException e) {
            throw new DelphixInterruptedException();
        }
    }

    /**
     * Get the SASL configuration used for authentication. By default, ANONYMOUS is used.
     */
    protected ClientSaslMechanism getSaslConfig(String user, String password) {
        return new AnonymousClient();
    }

    /**
     * Get the SSL params used for encryption. By default, SSL is disabled.
     */
    protected SSLClientParams getSSLParams() {
        return null;
    }

    protected void setConfigurator(ClientConfigurator configurator) {
        this.configurator = configurator;
    }

    /**
     * Client configurator provides defaults for all service options used to configure a client. To customize the
     * service options, override this class, create an instance of it, and set the configurator to that instance.
     */
    public static class ClientConfigurator extends ServiceConfigurator {

        private static ClientConfigurator instance = new ClientConfigurator();

        // Service option default values
        protected static final int LOGIN_TIMEOUT_SECONDS = 60;
        protected static final int BANDWIDTH_LIMIT = 0;

        protected ClientConfigurator() {

        }

        public static ClientConfigurator getDefault() {
            return instance;
        }

        public int getLoginTimeoutSeconds() {
            return LOGIN_TIMEOUT_SECONDS;
        }

        public int getBandwidthLimit() {
            return BANDWIDTH_LIMIT;
        }
    }
}
