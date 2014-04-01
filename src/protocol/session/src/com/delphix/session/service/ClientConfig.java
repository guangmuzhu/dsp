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

import com.delphix.session.sasl.ClientSaslMechanism;
import com.delphix.session.ssl.SSLClientContext;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * This class describes the service client configuration or specification, including the SSL context, SASL mechanism,
 * service terminus, transport addresses, and service parameters.
 */
public class ClientConfig {

    private final ServiceTerminus client;
    private final ServiceTerminus server;

    private final Service service;
    private final Collection<ProtocolHandler<?>> protocolHandlers;

    private SSLClientContext sslContext;
    private ClientSaslMechanism saslMechanism;

    private String serverHost;

    private List<TransportAddress> addresses;

    private long timeout;

    private ServiceOptions options;

    private ExecutorService executor;

    public ClientConfig(ServiceTerminus client, ServiceTerminus server) {
        this(client, server, null, null);
    }

    public ClientConfig(ServiceTerminus client, ServiceTerminus server, Service service,
            Collection<ProtocolHandler<?>> handlers) {
        this.client = client;
        this.server = server;
        this.service = service;
        this.protocolHandlers = handlers;

        // Create the default service options
        options = ServiceOptions.getClientOptions();
    }

    public SSLClientContext getSslContext() {
        return sslContext;
    }

    public void setSslContext(SSLClientContext sslContext) {
        this.sslContext = sslContext;
    }

    public ClientSaslMechanism getSaslMechanism() {
        return saslMechanism;
    }

    public void setSaslMechanism(ClientSaslMechanism saslMechanism) {
        this.saslMechanism = saslMechanism;
    }

    public ServiceTerminus getClient() {
        return client;
    }

    public ServiceTerminus getServer() {
        return server;
    }

    public Service getService() {
        return service;
    }

    public Collection<ProtocolHandler<?>> getProtocolHandlers() {
        return protocolHandlers;
    }

    public String getServerHost() {
        return serverHost;
    }

    public void setServerHost(String serverHost) {
        this.serverHost = serverHost;
    }

    public List<TransportAddress> getAddresses() {
        return addresses;
    }

    public void setAddresses(List<TransportAddress> addresses) {
        this.addresses = addresses;
    }

    public long getLoginTimeout() {
        return timeout;
    }

    public void setLoginTimeout(long timeout) {
        this.timeout = timeout;
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
