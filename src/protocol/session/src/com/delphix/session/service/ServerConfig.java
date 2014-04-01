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

package com.delphix.session.service;

import com.delphix.session.net.NetServerConfig;
import com.delphix.session.sasl.SaslServerConfig;
import com.delphix.session.ssl.SSLServerContext;

import java.util.concurrent.ExecutorService;

/**
 * This class describes the server configuration, including SASL configuration, SSL context, network configuration,
 * and service parameters.
 */
public class ServerConfig {

    private final ServiceTerminus terminus;
    private final Service service;
    private final ProtocolHandlerFactory handlerFactory;

    private SSLServerContext sslContext;
    private SaslServerConfig saslConfig;
    private NetServerConfig netConfig;

    private ServiceOptions options;

    private ExecutorService executor;

    public ServerConfig() {
        this(null, null, null);
    }

    public ServerConfig(ServiceTerminus terminus, Service service, ProtocolHandlerFactory handlerFactory) {
        this.terminus = terminus;
        this.service = service;
        this.handlerFactory = handlerFactory;

        // Create the default service options
        options = ServiceOptions.getServerOptions();
    }

    public ServiceTerminus getTerminus() {
        return terminus;
    }

    public Service getService() {
        return service;
    }

    public ProtocolHandlerFactory getProtocolHandlerFactory() {
        return handlerFactory;
    }

    public SSLServerContext getSslContext() {
        return sslContext;
    }

    public void setSslContext(SSLServerContext sslContext) {
        this.sslContext = sslContext;
    }

    public SaslServerConfig getSaslConfig() {
        return saslConfig;
    }

    public void setSaslConfig(SaslServerConfig saslConfig) {
        this.saslConfig = saslConfig;
    }

    public NetServerConfig getNetConfig() {
        return netConfig;
    }

    public void setNetConfig(NetServerConfig netConfig) {
        this.netConfig = netConfig;
    }

    public ServiceOptions getOptions() {
        return options;
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }
}
