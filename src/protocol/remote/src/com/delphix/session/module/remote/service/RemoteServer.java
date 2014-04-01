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

package com.delphix.session.module.remote.service;

import com.delphix.session.module.remote.RemoteFactory;
import com.delphix.session.sasl.AnonymousServer;
import com.delphix.session.sasl.SaslServerConfig;
import com.delphix.session.service.ProtocolHandler;
import com.delphix.session.service.ProtocolHandlerFactory;
import com.delphix.session.service.ServerManager;
import com.delphix.session.service.ServiceTerminus;
import com.delphix.session.ssl.SSLServerParams;
import com.delphix.session.util.AbstractServer;
import com.delphix.session.util.TaggedRequestExecutor;
import com.google.common.collect.ImmutableList;

import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static com.delphix.session.service.ServiceProtocol.PROTOCOL;

public class RemoteServer extends AbstractServer {

    private RemoteFactory remoteFactory;

    private String keyStorePath;
    private String keyStorePass;

    public RemoteServer() {
        super(RemoteServiceType.getInstance(), RemoteExchangeType.class);
    }

    // Spring injected dependencies
    public void setServerManager(ServerManager manager) {
        this.manager = manager;
    }

    public void setRemoteFactory(RemoteFactory remoteFactory) {
        this.remoteFactory = remoteFactory;
    }

    public void setKeyStorePath(String keyStorePath) {
        this.keyStorePath = keyStorePath;
    }

    public void setKeyStorePass(String keyStorePass) {
        this.keyStorePass = keyStorePass;
    }

    @Override
    protected ProtocolHandlerFactory getProtocolHandlerFactory() {
        return new ProtocolHandlerFactory() {
            @Override
            public List<? extends ProtocolHandler<?>> getHandlers(ServiceTerminus terminus) {
                return ImmutableList.of(remoteFactory.createServer(getServiceExecutor()));
            }
        };
    }

    @Override
    protected SaslServerConfig getSaslConfig() {
        SaslServerConfig sasl = new SaslServerConfig(PROTOCOL, SERVER);
        sasl.addMechanism(new AnonymousServer());
        return sasl;
    }

    @Override
    protected SSLServerParams getSSLParams() {
        SSLServerParams params = new SSLServerParams();

        // Get URL for the keyStorePath resource
        URL url = getClass().getResource(keyStorePath);

        params.setKeyStorePath(url.toString());
        params.setKeyStorePass(keyStorePass);
        params.setKeyPass(keyStorePass);

        return params;
    }

    @Override
    protected ExecutorService getProtocolExecutor() {
        return new TaggedRequestExecutor();
    }
}
