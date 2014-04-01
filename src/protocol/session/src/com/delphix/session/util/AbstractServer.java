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

import com.delphix.appliance.server.util.ExceptionUtil;
import com.delphix.session.sasl.AnonymousServer;
import com.delphix.session.sasl.SaslServerConfig;
import com.delphix.session.service.*;
import com.delphix.session.ssl.SSLContextFactory;
import com.delphix.session.ssl.SSLServerContext;
import com.delphix.session.ssl.SSLServerParams;
import com.delphix.session.ssl.TransportSecurityLevel;

import java.util.List;

import static com.delphix.session.service.ServiceOption.*;
import static com.delphix.session.service.ServiceProtocol.PROTOCOL;

/**
 * This implements the server side of an abstract session service. It provides service registration with the core
 * DSP framework, service options applicable to server side only, service options refresh mechanism, as well as
 * server side security configuration interfaces including authentication and encryption.
 *
 * This class requires access to the ServerManager instance. The concrete server that extends this class is expected
 * to instantiate this instance. It may do so via Spring injection, regular constructor, setter method, or any other
 * possible ways.
 *
 * For examples, please refer to the com.delphix.session.module.remote module for how to instantiate a concrete server.
 */
public abstract class AbstractServer extends AbstractService {

    // Protocol server manager and export server instance
    protected ServerManager manager;
    protected Server server;

    protected ServerConfigurator configurator;

    public AbstractServer(ServiceType type, ServiceCodec codec) {
        super(type, codec);

        configurator = ServerConfigurator.getDefault();
    }

    public <E extends Enum<E> & ExchangeType> AbstractServer(ServiceType type, Class<E> clazz) {
        super(type, clazz);

        configurator = ServerConfigurator.getDefault();
    }

    @Override
    public void start() {
        super.start();

        // Register the service
        register();
    }

    @Override
    public void stop() {
        // Shutdown the service
        server.shutdown();

        super.stop();
    }

    /**
     * Implementations should override this method to provide a ProtocolHandlerFactory that will be
     * used to get the appropriate ProtocolHandler for new sessions.
     */
    protected abstract ProtocolHandlerFactory getProtocolHandlerFactory();

    protected void register() {
        ServerConfig config = new ServerConfig(type.getServiceName(), this, getProtocolHandlerFactory());

        // Configure SASL
        config.setSaslConfig(getSaslConfig());

        // Configure SSL
        SSLServerParams params = getSSLParams();

        if (params != null) {
            try {
                SSLServerContext ssl = SSLContextFactory.getServerContext(params);
                config.setSslContext(ssl);
            } catch (Throwable t) {
                logger.errorf(t, "failed to initialize SSL context");
                throw ExceptionUtil.getDelphixException(t);
            }
        }

        // Configure custom command executor
        if (protocolExecutor != null) {
            config.setExecutor(protocolExecutor);
        }

        ServiceOptions offer = config.getOptions();

        offer.setOption(SYNC_DISPATCH, configurator.getCommandSyncDispatch());
        offer.setOption(LOGOUT_TIMEOUT, configurator.getLogoutTimeoutSeconds());

        offer.setOption(HEADER_DIGEST, configurator.getHeaderDigestMethods());
        offer.setOption(FRAME_DIGEST, configurator.getFrameDigestMethods());
        offer.setOption(PAYLOAD_DIGEST, configurator.getPayloadDigestMethods());
        offer.setOption(DIGEST_DATA, configurator.getPayloadDigestData());

        // Configurable options
        refresh(config);

        // Register the service
        server = manager.register(config);

        // Add the nexus listener if any
        NexusListener listener = getNexusListener();

        if (listener != null) {
            server.addListener(listener);
        }
    }

    protected void refresh(ServerConfig config) {
        SSLServerContext ssl = config.getSslContext();

        if (ssl != null) {
            if (configurator.isEncryptionEnabled()) {
                ssl.setTlsLevel(TransportSecurityLevel.ENCRYPTION);
            } else {
                ssl.setTlsLevel(TransportSecurityLevel.AUTHENTICATION);
            }
        }

        ServiceOptions offer = config.getOptions();

        List<String> compress;

        if (configurator.isCompressionEnabled()) {
            compress = configurator.getPayloadCompressMethods();
        } else {
            compress = configurator.getPayloadCompressNone();
        }

        offer.setOption(PAYLOAD_COMPRESS, compress);

        offer.setOption(FORE_QUEUE_DEPTH, configurator.getForeCommandQueueDepth());
        offer.setOption(FORE_MAX_REQUEST, configurator.getForeMaxRequestSize());
        offer.setOption(BACK_QUEUE_DEPTH, configurator.getBackCommandQueueDepth());
        offer.setOption(BACK_MAX_REQUEST, configurator.getBackMaxRequestSize());
        offer.setOption(MIN_KEEPALIVE_TIME, configurator.getAPDTimeoutSeconds());
        offer.setOption(SOCKET_SEND_BUFFER, configurator.getSendBufferSize());
        offer.setOption(SOCKET_RECEIVE_BUFFER, configurator.getReceiveBufferSize());

        logger.infof("service options: %s", offer.toString());
    }

    /**
     * Refresh the server configuration. This is called when a service option has changed that consequently affects
     * the behavior of the service. The application is responsible for change management.
     */
    public void refresh() {
        Server server = manager.locate(type.getServiceName());
        ServerConfig config = server.getConfig();

        refresh(config);
    }

    /**
     * Get the SASL server configuration for authentication. By default, ANONYMOUS is used.
     */
    protected SaslServerConfig getSaslConfig() {
        SaslServerConfig sasl = new SaslServerConfig(PROTOCOL, SERVER);
        sasl.addMechanism(new AnonymousServer());
        return sasl;
    }

    /**
     * Get the SSL server parameters for encryption. By default, SSL is disabled.
     */
    protected SSLServerParams getSSLParams() {
        return null;
    }

    @Override
    protected NexusListener getNexusListener() {
        return new ServerNexusListener();
    }

    protected class ServerNexusListener extends SessionNexusListener {

        public ServerNexusListener() {
        }

        @Override
        public void nexusLogout(ServiceNexus nexus) {
            super.nexusLogout(nexus);

            // Respond to logout
            ((ServerNexus) nexus).logout();
        }
    }

    protected void setConfigurator(ServerConfigurator configurator) {
        this.configurator = configurator;
    }

    /**
     * Server configurator provides defaults for all service options used to configure a server. To customize the
     * service options, override this class, create an instance of it, and set the configurator to that instance.
     */
    public static class ServerConfigurator extends ServiceConfigurator {

        private static ServerConfigurator instance = new ServerConfigurator();

        protected ServerConfigurator() {

        }

        public static ServerConfigurator getDefault() {
            return instance;
        }
    }
}
