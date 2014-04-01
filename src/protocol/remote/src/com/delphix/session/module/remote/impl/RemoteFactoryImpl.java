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

package com.delphix.session.module.remote.impl;

import com.delphix.session.module.remote.RemoteFactory;
import com.delphix.session.module.remote.RemoteManager;
import com.delphix.session.module.remote.RemoteProtocolClient;
import com.delphix.session.module.remote.RemoteProtocolServer;
import com.delphix.session.service.ServiceNexus;

import java.util.concurrent.ExecutorService;

public class RemoteFactoryImpl implements RemoteFactory {

    @Override
    public RemoteProtocolClient createClient(ExecutorService executor) {
        return new RemoteProtocolClientImpl(executor);
    }

    @Override
    public RemoteProtocolServer createServer(ExecutorService executor) {
        return new RemoteProtocolServerImpl(executor);
    }

    @Override
    public RemoteManager createManager(RemoteProtocolClient remoteClient, ServiceNexus nexus) {
        RemoteProtocolClientImpl impl = (RemoteProtocolClientImpl) remoteClient;
        return impl.createRemoteManager(nexus);
    }
}
