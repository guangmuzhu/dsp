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
 * This class describes an idempotent retry exception. An idempotent request may not find the corresponding response
 * available when it is retried upon a transport reset. A protocol provider should throw this exception if recovery
 * fails for this reason so that the application may retry this command on its own if necessary.
 */
public class IdempotentRetryException extends ServiceDeliveryException {

    public IdempotentRetryException() {
        super();
    }

    public IdempotentRetryException(String message) {
        super(message);
    }

    public IdempotentRetryException(String message, Throwable cause) {
        super(message, cause);
    }

    public IdempotentRetryException(Throwable cause) {
        super(cause);
    }
}
