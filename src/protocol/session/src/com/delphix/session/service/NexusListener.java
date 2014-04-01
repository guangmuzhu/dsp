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
 * This interface describes the nexus listener which subscribes to service nexus related events. This is intended
 * to be implemented by a nexus concentrator such as a server which is interested in nexus lifecycle management.
 * Events on the nexus are guaranteed to be delivered in the same chronological order as they occur in real time.
 */
public interface NexusListener {

    /**
     * A nexus has been established.
     */
    public void nexusEstablished(ServiceNexus nexus);

    /**
     * The nexus has been closed.
     */
    public void nexusClosed(ServiceNexus nexus);

    /**
     * Connectivity within the nexus has been at least partially restored. In other words, the nexus has transitioned
     * out of the all path down state.
     */
    public void nexusRestored(ServiceNexus nexus);

    /**
     * Connectivity within the nexus has been completely lost. In other words, the nexus has transitioned into the
     * all path down state but the nexus state will be kept intact.
     */
    public void nexusLost(ServiceNexus nexus);

    /**
     * An existing service nexus has been reinstated by a new one. This event is server side only.
     */
    public void nexusReinstated(ServiceNexus existing, ServiceNexus replacement);

    /**
     * Logout is requested on the service nexus. This event is server side only.
     */
    public void nexusLogout(ServiceNexus nexus);

    /**
     * Check if the service listener is one shot only. A one shot listener automatically unregisters itself after
     * the delivery of the first event.
     */
    public boolean isOneShot();
}
