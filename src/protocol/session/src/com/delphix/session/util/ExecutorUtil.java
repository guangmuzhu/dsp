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

package com.delphix.session.util;

import com.delphix.platform.PlatformManagerLocator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ExecutorUtil {

    private static final long SERVICE_SHUTDOWN_TIMEOUT = 15;

    /**
     * Shutdown the executor service.
     */
    public static void shutdown(ExecutorService service) throws TimeoutException {
        // Shutdown the service and disable new tasks from being submitted
        service.shutdown();

        try {
            // Wait for the existing tasks to terminate if any
            if (!service.awaitTermination(SERVICE_SHUTDOWN_TIMEOUT, TimeUnit.SECONDS)) {
                // Forcefully cancel existing tasks
                service.shutdownNow();

                // Wait a while longer for the tasks to respond to the cancellation
                if (!service.awaitTermination(SERVICE_SHUTDOWN_TIMEOUT, TimeUnit.SECONDS)) {
                    throw new TimeoutException("failed to shutdown the executor service " + service);
                }
            }
        } catch (InterruptedException e) {
            // (Re)cancel the existing tasks if the current thread was interrupted
            service.shutdownNow();

            // Preserve the interrupt status
            PlatformManagerLocator.getInterruptStrategy().interrupt(Thread.currentThread());
        }
    }
}
