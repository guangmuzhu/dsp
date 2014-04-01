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

import com.delphix.session.control.NexusInfo;
import com.delphix.session.control.NexusStats;

import java.util.Collection;

/**
 * This interface describes a logical communication channel established between two entities or peers. The nexus
 * attaches to an entity at a terminus. There are two kinds of terminus, namely, client and server. The creation
 * of a nexus can only be initiated from the entity that owns the client terminus. Other than that, the nexus is
 * symmetrical in terms of service execution.
 */
public interface ServiceNexus extends ServiceExecutor {

    /**
     * Get the client terminus.
     */
    public ServiceTerminus getClientTerminus();

    /**
     * Get the service terminus.
     */
    public ServiceTerminus getServerTerminus();

    /**
     * Get the transport connections.
     */
    public Collection<ServiceTransport> getTransports();

    /**
     * Check if the service nexus belongs to the client.
     */
    public boolean isClient();

    /**
     * Check if the service nexus is connected. Here we are referring to connectivity at the nexus level as opposed to
     * at the transport level. A nexus that is connected is capable of command delivery and other normal activities.
     */
    public boolean isConnected();

    /**
     * Check if the service nexus is degraded or only partially connected. This is for diagnostic and informational
     * purposes so that an application monitoring the nexus state can correlate it with the behavior change observed
     * at a higher level.
     */
    public boolean isDegraded();

    /**
     * Close the service nexus.
     */
    public CloseFuture close();

    /**
     * Get the service available via the service nexus.
     */
    public Service getService();

    /**
     * Get the service options.
     */
    public ServiceOptions getOptions();

    /**
     * Check if the service nexus is closed or closing.
     */
    public boolean isClosed();

    /**
     * Get the stats.
     */
    public NexusStats getStats();

    /**
     * Reset the stats.
     */
    public void resetStats();

    /**
     * Get information about the nexus.
     */
    public NexusInfo getInfo();

    /**
     * Get the peer stats.
     */
    public NexusStats getPeerStats();

    /**
     * Reset the peer stats.
     */
    public void resetPeerStats();

    /**
     * Get information about the peer nexus.
     */
    public NexusInfo getPeerInfo();

    /**
     * Get the protocol handler that should handle requests received on this nexus.
     */
    public <T extends ProtocolHandler<T>> T getProtocolHandler(Class<T> iface);
}
