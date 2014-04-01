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
 * This interface describes all service specific responses. A specific service should implement this interface for all
 * response types that it supports. An object of the ServiceResponse type is transferred from the service in response
 * to the ServiceRequest as the payload of the CommandResponse frame. It is then reconstructed on the requester before
 * a callback is made. The service response also provides optional bulk data support in the flexible scatter-gather
 * form. Unlike the regular non-data portion of the service response, bulk data is sent as is without incurring
 * serialization overhead.
 */
public interface ServiceResponse extends ServiceExchange {

    /**
     * Get the optional scatter-gather data buffers.
     */
    public ByteBuffer[] getData();

    /**
     * Set the optional scatter-gather data buffers.
     */
    public void setData(ByteBuffer[] data);
}
