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
 * This interface describes the nexus registry which contains a collection of server nexi each with a unique client.
 */
public interface ServerNexusRegistry {

    /**
     * Get the set of server nexi one for each client.
     */
    public Set<ServerNexus> getClients();

    /**
     * Get the set of client termini.
     */
    public Set<ServiceTerminus> getTermini();

    /**
     * Locate the server nexus by the client terminus.
     */
    public ServerNexus locate(ServiceTerminus terminus);

    /**
     * Locate the server nexus by the name.
     */
    public ServerNexus locate(String name);

    /**
     * Check if the registry is empty.
     */
    public boolean isEmpty();
}
