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

import com.delphix.appliance.logger.Logger;
import com.delphix.session.service.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

/**
 * This implements an abstract session service. It is extended by AbstractServer and AbstractConnector, for server
 * and client side, respectively. Among other things, this class provides an executor service, default values for
 * most service options and interfaces for accessing and overriding.
 *
 * Service request dispatching has the following flow.
 *
 *     DSP framework
 *         ServiceRequest.execute()
 *             ProtocolClient/ProtocolServer request-specific interface
 */
public abstract class AbstractService implements Service {

    protected static final Logger logger = Logger.getLogger(AbstractService.class);

    protected static final String SERVER = "server.domain";

    protected final ServiceType type;
    protected final ServiceCodec codec;

    protected ExecutorService serviceExecutor;
    protected ExecutorService protocolExecutor;

    protected AbstractService(ServiceType type, ServiceCodec codec) {
        this.type = type;
        this.codec = codec;
    }

    protected <E extends Enum<E> & ExchangeType> AbstractService(ServiceType type, Class<E> clazz) {
        this(type, new ExchangeCodec(ExchangeRegistry.create(clazz)));
    }

    /**
     * Fire up the executors.
     */
    public void start() {
        serviceExecutor = Executors.newCachedThreadPool();
        protocolExecutor = getProtocolExecutor();
    }

    /**
     * Shutdown the executors.
     */
    public void stop() {
        if (protocolExecutor != null) {
            try {
                ExecutorUtil.shutdown(protocolExecutor);
            } catch (TimeoutException e) {
                logger.errorf("failed to shutdown executor service %s", protocolExecutor);
            }
        }

        try {
            ExecutorUtil.shutdown(serviceExecutor);
        } catch (TimeoutException e) {
            logger.errorf("failed to shutdown executor service %s", serviceExecutor);
        }
    }

    /**
     * Return a general purpose thread pool executor that may be used to process asynchronous tasks in the service
     * implementation.
     */
    public ExecutorService getServiceExecutor() {
        return serviceExecutor;
    }

    @Override
    public ServiceType getType() {
        return type;
    }

    @Override
    public ServiceCodec getCodec() {
        return codec;
    }

    /**
     * Return the executor that processes the protocol requests delivered upstream from DSP. By default, a global
     * thread pool executor is used. Override this method to return a custom executor if desired. For example, one
     * may want to use the TaggedRequestExecutor to ensure end-to-end ordering if the application service involves
     * data streaming.
     */
    protected ExecutorService getProtocolExecutor() {
        return null;
    }

    /**
     * Get the service nexus event listener.
     */
    protected NexusListener getNexusListener() {
        return new SessionNexusListener();
    }

    protected class SessionNexusListener extends DefaultNexusListener {

        @Override
        public void nexusEstablished(ServiceNexus nexus) {
            logger.infof("%s: established", nexus);
        }

        @Override
        public void nexusLogout(ServiceNexus nexus) {
            logger.infof("%s: logout requested", nexus);
        }

        @Override
        public void nexusClosed(ServiceNexus nexus) {
            logger.infof("%s: closed", nexus);
        }

        @Override
        public void nexusRestored(ServiceNexus nexus) {
            logger.infof("%s: restored", nexus);
        }

        @Override
        public void nexusLost(ServiceNexus nexus) {
            logger.infof("%s: lost", nexus);
        }

        @Override
        public void nexusReinstated(ServiceNexus existing, ServiceNexus replacement) {
            logger.infof("%s: reinstated by %s", existing, replacement);
        }
    }

    /**
     * Service configurator provides defaults for all service options common to both client and server.
     */
    protected static class ServiceConfigurator {

        /*
         * The following are default values for various service options. They are not directly accessed. Instead, the
         * actual values for service options are always accessed via the correspondingly named getter methods, which
         * return the values defined here by default. An application that wants to use different values than default
         * may override the getter method. If the values are dynamic, the server options may need to be refreshed in
         * order for the change to take effect.
         */
        protected static final int APD_TIMEOUT_SECONDS = 60 * 60;
        protected static final int LOGOUT_TIMEOUT_SECONDS = 5;
        protected static final boolean COMMAND_SYNC_DISPATCH = true;

        protected static final boolean ENCRYPTION_ENABLED = false;
        protected static final boolean COMPRESSION_ENABLED = false;

        protected static final int COMMAND_QUEUE_DEPTH = 64;
        protected static final int MAX_REQUEST_SIZE = 64 * 1024;
        protected static final int SEND_BUFFER_SIZE = 256 * 1024;
        protected static final int RECEIVE_BUFFER_SIZE = 256 * 1024;

        protected static final int RECOVERY_INTERVAL = 10 * 1000;
        protected static final int RECOVERY_TIMEOUT = 300 * 1000;

        protected static final String[] HEADER_DIGEST_METHODS = { "DIGEST_CRC32", "DIGEST_ADLER32", "DIGEST_NONE" };
        protected static final String[] FRAME_DIGEST_METHODS = { "DIGEST_ADLER32", "DIGEST_CRC32", "DIGEST_NONE" };
        protected static final String[] PAYLOAD_DIGEST_METHODS = { "DIGEST_ADLER32", "DIGEST_CRC32", "DIGEST_NONE" };
        protected static final boolean PAYLOAD_DIGEST_DATA = false;

        protected static final String[] PAYLOAD_COMPRESS_METHODS =
        { "COMPRESS_LZ4", "COMPRESS_DEFLATE", "COMPRESS_NONE" };
        protected static final String[] PAYLOAD_COMPRESS_NONE = { "COMPRESS_NONE" };

        protected static final String TRANSPORT_SCHEDULER = "ROUND_ROBIN";

        public int getAPDTimeoutSeconds() {
            return APD_TIMEOUT_SECONDS;
        }

        public int getLogoutTimeoutSeconds() {
            return LOGOUT_TIMEOUT_SECONDS;
        }

        public boolean getCommandSyncDispatch() {
            return COMMAND_SYNC_DISPATCH;
        }

        public int getRecoveryInterval() {
            return RECOVERY_INTERVAL;
        }

        public int getRecoveryTimeout() {
            return RECOVERY_TIMEOUT;
        }

        public List<String> getHeaderDigestMethods() {
            return Arrays.asList(HEADER_DIGEST_METHODS);
        }

        public List<String> getFrameDigestMethods() {
            return Arrays.asList(FRAME_DIGEST_METHODS);
        }

        public List<String> getPayloadDigestMethods() {
            return Arrays.asList(PAYLOAD_DIGEST_METHODS);
        }

        public boolean getPayloadDigestData() {
            return PAYLOAD_DIGEST_DATA;
        }

        public List<String> getPayloadCompressMethods() {
            return Arrays.asList(PAYLOAD_COMPRESS_METHODS);
        }

        public List<String> getPayloadCompressNone() {
            return Arrays.asList(PAYLOAD_COMPRESS_NONE);
        }

        public int getForeCommandQueueDepth() {
            return COMMAND_QUEUE_DEPTH;
        }

        public int getForeMaxRequestSize() {
            return MAX_REQUEST_SIZE;
        }

        public int getBackCommandQueueDepth() {
            return COMMAND_QUEUE_DEPTH;
        }

        public int getBackMaxRequestSize() {
            return MAX_REQUEST_SIZE;
        }

        public int getSendBufferSize() {
            return SEND_BUFFER_SIZE;
        }

        public int getReceiveBufferSize() {
            return RECEIVE_BUFFER_SIZE;
        }

        public boolean isEncryptionEnabled() {
            return ENCRYPTION_ENABLED;
        }

        public boolean isCompressionEnabled() {
            return COMPRESSION_ENABLED;
        }

        public String getTransportScheduler() {
            return TRANSPORT_SCHEDULER;
        }
    }
}
