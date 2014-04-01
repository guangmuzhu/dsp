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
 * This interface describes the client side representation of a service nexus. While there is a single service nexus
 * between the client and the server, each has a its own representation which reflects the role of the participant.
 * In addition to the service nexus related functionalities, the client nexus provides remote execution support for
 * client initiated commands and delivers server initiated commands to the registered service. A client nexus may be
 * referred to as service client or simply client.
 */
public interface ClientNexus extends ServiceNexus {

    /**
     * Get the service spec.
     */
    public ClientConfig getConfig();

    /**
     * Add the nexus event listener.
     */
    public void addListener(NexusListener listener);

    /**
     * Remove the nexus event listener.
     */
    public void removeListener(NexusListener listener);

    /**
     * Attempt to login to the service server.
     */
    public LoginFuture login();
}
