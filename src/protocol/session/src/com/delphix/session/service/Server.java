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

import com.delphix.session.control.ServerInfo;

/**
 * This interface describes the server offering a service. It has a service terminus for naming and identification
 * purposes, a set of server nexuses, one for each client currently connected to the server, and the service offering
 * implemented by the application. A server that offers a service may be referred to simply as a service when it is
 * not confused with the service offered by the client over the backchannel.
 */
public interface Server extends ServerNexusRegistry {

    /**
     * Get the service terminus.
     */
    public ServiceTerminus getTerminus();

    /**
     * Get the service offered by this server.
     */
    public Service getService();

    /**
     * Get the service configuration.
     */
    public ServerConfig getConfig();

    /**
     * Add the nexus event listener.
     */
    public void addListener(NexusListener listener);

    /**
     * Remove the nexus event listener.
     */
    public void removeListener(NexusListener listener);

    /**
     * Shutdown the service server.
     */
    public void shutdown();

    /**
     * Get information about the server.
     */
    public ServerInfo getInfo();
}
