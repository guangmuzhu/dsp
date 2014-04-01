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
 * This class describes the service delivery exception. As the name suggests, the service delivery exception refers to
 * condition pertaining to the delivery of a command over the service nexus as opposed to the execution of the command
 * itself. The protocol provider should map a protocol specific condition to one of the service delivery exceptions if
 * the condition requires action from the application.
 */
public class ServiceDeliveryException extends ServiceException {

    public ServiceDeliveryException() {
        super();
    }

    public ServiceDeliveryException(String message) {
        super(message);
    }

    public ServiceDeliveryException(Throwable cause) {
        super(cause);
    }

    public ServiceDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
