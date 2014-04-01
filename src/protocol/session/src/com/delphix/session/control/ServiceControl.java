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

public interface ServiceControl {

    /**
     * Get information about the nexus with the given name.
     */
    public NexusInfo getNexus(String name);

    /**
     * Get stats for the nexus with the given name.
     */
    public NexusStats getStats(String name);

    /**
     * Reset stats for the nexus with the given name.
     */
    public void resetStats(String name);

    /**
     * Get information about the peer of the nexus with the given name.
     */
    public NexusInfo getPeerNexus(String name);

    /**
     * Get stats for the peer of the nexus with the given name.
     */
    public NexusStats getPeerStats(String name);

    /**
     * Reset stats for the peer of the nexus with the given name.
     */
    public void resetPeerStats(String name);
}
