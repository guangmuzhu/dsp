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

/**
 * This interface describes the server side representation of a service nexus. While there is a single service nexus
 * between the client and the server, each has its own representation which reflects the role of the participant.
 * In addition to the service nexus related functionalities, the server nexus delivers client initiated commands
 * to the registered service and provides remote execution support for server initiated commands.
 */
public interface ServerNexus extends ServiceNexus {

    /**
     * Get the server to which the nexus belongs.
     */
    public Server getServer();

    /**
     * Signal the readiness to proceed with session logout initiated by the client.
     */
    public void logout();

    /**
     * Check if logout has been requested on the server nexus.
     */
    public boolean isLogoutRequested();

    /**
     * Check if logout has been completed on the server nexus.
     */
    public boolean isLogoutComplete();

    /**
     * Return the name of the server nexus authenticated user.
     */
    public String getUserName();
}
