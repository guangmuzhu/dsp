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

import java.util.Set;

/**
 * This interface describes the service client manager. It is responsible for the lifecycle management of service
 * clients.
 */
public interface ClientManager {

    /**
     * Start the client manager.
     */
    public void start();

    /**
     * Create a service client with the given service spec.
     */
    public ClientNexus create(ClientConfig spec);

    /**
     * Remove the service client by the client and server terminus.
     */
    public void remove(ServiceTerminus client, ServiceTerminus server);

    /**
     * Locate the service client by the client and server terminus.
     */
    public ClientNexus locate(ServiceTerminus client, ServiceTerminus server);

    /**
     * Locate the service client by the name.
     */
    public ClientNexus locate(String name);

    /**
     * Get the set of service clients.
     */
    public Set<ClientNexus> getClients();

    /**
     * Check if there are any service clients.
     */
    public boolean isEmpty();

    /**
     * Get the default service terminus for use with the service clients.
     */
    public ServiceTerminus getTerminus();

    /**
     * Stop the client manager.
     */
    public void stop();
}
