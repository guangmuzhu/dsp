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

package com.delphix.appliance.server.util;

/**
 * ExceptionUtil.unwrap() replaces the causes of ExecutionExceptions with exceptions of this type and
 * has the cause replace the ExecutionException. When you see an Exception stack of the form:
 *
 * RealException
 *   Caused by: UnwrappedExecutionException
 *     Caused by: UnderlyingException
 *
 * It means that RealException was thrown by some subsystem which wrapped it in an ExecutionException:
 *
 * ExecutionException
 *   Caused by: RealException
 *     Caused by: UnderlyingException
 *
 * The ExecutionException was then caught and "unwrapped" at which point the RealException was replaced
 * by the UnwrappedExecutionException, and the RealException replaced the ExecutionException.
 *
 * See ExceptionUtil.unwrap() for more details.
 */
public class UnwrappedExecutionException extends Exception {

    public UnwrappedExecutionException(Class<?> originalException, Throwable cause) {
        super(String.format("(originally: %s)", originalException.getCanonicalName()), cause);
    }
}
