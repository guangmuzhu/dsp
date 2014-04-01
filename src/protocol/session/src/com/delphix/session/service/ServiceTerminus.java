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

import java.io.Externalizable;

/**
 * This interface describes the end point of a service nexus. Service terminus serves to name and identify either
 * end point of a service nexus and therefore must remain unique at least within the operational domain of the
 * communication protocol instance in question. Service terminus is a nexus level end point from the perspective
 * of the networking architecture. It is decoupled from the transport end point so that service can be identified,
 * accessed, and managed regardless of the transport addressing. Think of a service hosted in clustered environment
 * where it could migrate among nodes with different transport addresses. A service would register itself with a
 * service terminus and the service terminus will be used by any client connecting to the service for identification
 * purpose. Client must also have its own terminus which is used by the server for nexus state management. For
 * example, the server may decide whether to reinstate an existing nexus if a new nexus creation attempt has
 * arrived from the same client as identified by the client terminus.
 *
 * Service terminus may take different forms and therefore offer different properties. The following are examples of
 * the differences.
 *
 *   - string based (for example IQN) v.s. binary (for example UUID)
 *   - universally unique or locally unique
 *   - ephemeral or persistent
 */
public interface ServiceTerminus extends Externalizable {

    /**
     * Check if the service terminus is universally unique.
     */
    public boolean isUniversal();

    /**
     * Check if the service terminus is ephemeral.
     */
    public boolean isEphemeral();

    /**
     * Get the friendly alias for the service terminus.
     */
    public String getAlias();

    /**
     * Get the byte array representation of the service terminus.
     */
    public byte[] getBytes();
}
