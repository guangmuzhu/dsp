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

package com.delphix.session.control;

import java.util.List;

public interface ServerControl extends ServiceControl {

    /**
     * Get the list of servers registered with the protocol.
     */
    public List<String> listServers();

    /**
     * Get the list of clients connected to all servers.
     */
    public List<String> listClients();

    /**
     * Get the list of clients connected to the server with the given name.
     */
    public List<String> listClients(String name);

    /**
     * Get information about the server with the given name.
     */
    public ServerInfo getServer(String name);
}
