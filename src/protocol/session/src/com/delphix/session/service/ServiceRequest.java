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

import java.nio.ByteBuffer;

/**
 * This interface describes all service specific requests. A specific service should implement this interface for all
 * request types that it supports. An object of the ServiceRequest type is transferred from its creator to the peer as
 * the payload of the CommandRequest frame. It is then reconstructed on the other side before being dispatched through
 * the nexus's service entry point. The service request also provides optional bulk data support in the flexible
 * scatter-gather form. Unlike the regular non-data portion of the service request, bulk data is sent as is without
 * incurring serialization overhead.
 */
public interface ServiceRequest extends ServiceExchange {

    /**
     * Check if the service request is idempotent or not. An idempotent request may be processed multiple times and
     * the outcome would be the same.
     */
    public boolean isIdempotent();

    /**
     * Get the optional scatter-gather data buffers.
     */
    public ByteBuffer[] getData();

    /**
     * Set the optional scatter-gather data buffers.
     */
    public void setData(ByteBuffer[] data);

    /**
     * Process the service request which arrived on the specified service nexus. Return a service response if
     * successful. Otherwise, throw a service exception.
     */
    public ServiceResponse execute(ServiceNexus nexus);
}
