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

import com.delphix.session.service.ClientManager;
import com.delphix.session.ssl.SSLClientParams;
import com.delphix.session.util.AbstractConnector;
import com.delphix.session.util.TaggedRequestExecutor;

import java.net.URL;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

public class RemoteConnector extends AbstractConnector {

    private static final String CLIENT_NAME = "File Connector";

    private String trustStorePath;
    private String trustStorePass;

    public RemoteConnector() {
        this(null);
    }

    public RemoteConnector(UUID client) {
        super(RemoteServiceType.getInstance(), RemoteExchangeType.class, client, CLIENT_NAME);
    }

    // Spring injected dependencies
    public void setClientManager(ClientManager manager) {
        this.manager = manager;
    }

    public void setTrustStorePath(String trustStorePath) {
        this.trustStorePath = trustStorePath;
    }

    public void setTrustStorePass(String trustStorePass) {
        this.trustStorePass = trustStorePass;
    }

    @Override
    protected SSLClientParams getSSLParams() {
        SSLClientParams params = new SSLClientParams();

        // Get URL for the trustStorePath resource
        URL url = getClass().getResource(trustStorePath);

        params.setTrustStorePath(url.toString());
        params.setTrustStorePass(trustStorePass);

        return params;
    }

    @Override
    protected ExecutorService getProtocolExecutor() {
        return new TaggedRequestExecutor();
    }
}
