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

package com.delphix.session.module.remote;

import com.delphix.session.service.ServiceNexus;

import java.util.concurrent.ExecutorService;

/**
 * Remote factory for instantiation of protocol client, server and manager implementations.
 */
public interface RemoteFactory {

    /**
     * Create a protocol client.
     */
    public RemoteProtocolClient createClient(ExecutorService executor);

    /**
     * Create a protocol server.
     */
    public RemoteProtocolServer createServer(ExecutorService executor);

    /**
     * Create a file manager.
     */
    public RemoteManager createManager(RemoteProtocolClient client, ServiceNexus nexus);
}
