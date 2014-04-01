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

package com.delphix.session.module.remote.exception;

import com.delphix.session.service.ServiceExecutionException;

/**
 * This defines the root of the exception hierarchy for all exceptions incurred while serving protocol requests
 * defined in this service module. Specific protocol exception, descending from the root, should be defined for
 * each failure scenario to facilitate fine-grained error handling.
 *
 *     RemoteProtocolException
 *         |
 *         + StreamInterruptedException
 *         |
 *         + StreamIOException
 *         |
 *         + StreamNotFoundException
 *         |
 *         + StreamNotStartedException
 *         |
 *         + ...
 *
 * The protocol exception are intended for internal consumption by the protocol service implementation. Some may
 * be recovered internally in which case there are not "leaked" outside of the service implementation. Others may
 * be converted to externally visible exceptions defined at the API level.
 */
public class RemoteProtocolException extends ServiceExecutionException {

    public RemoteProtocolException() {
        super();
    }

    public RemoteProtocolException(String message, Throwable cause) {
        super(message, cause);
    }

    public RemoteProtocolException(String message) {
        super(message);
    }

    public RemoteProtocolException(Throwable cause) {
        super(cause);
    }
}
