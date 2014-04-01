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
 * This interface describes the service executor. It supports asynchronous command execution with optional callback
 * and timeout. A service future is returned as a result of the execution which supports the standard java future
 * style execution control such as result retrieval, execution cancellation, status inquiry, and so on.
 */
public interface ServiceExecutor {

    /**
     * Execute the service request.
     */
    public ServiceFuture execute(ServiceRequest request);

    /**
     * Execute the service request with an optional callback.
     */
    public ServiceFuture execute(ServiceRequest request, Runnable done);

    /**
     * Execute the service request with an optional callback and timeout.
     */
    public ServiceFuture execute(ServiceRequest request, Runnable done, long timeout);
}
