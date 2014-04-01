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

import java.util.Collection;

/**
 * This interface describes the server manager. It is responsible for the lifecycle and configuration management of
 * the protocol stack instance through which all registered servers are accessed.
 */
public interface ServerManager extends ServerRegistry {

    /**
     * Start the service server manager.
     */
    public void start();

    /**
     * Get the default server configuration.
     */
    public ServerConfig getServerConfig();

    /**
     * Get the TCP port through which servers will be accessed.
     */
    public int getServerPort();

    /**
     * Get the clients.
     */
    public Collection<ServerNexus> getClients();

    /**
     * Locate the client nexus by the name.
     */
    public ServerNexus locateClient(String name);

    /**
     * Get the transport connections.
     */
    public Collection<ServiceTransport> getTransports();

    /**
     * Stop the service manager.
     */
    public void stop();
}
