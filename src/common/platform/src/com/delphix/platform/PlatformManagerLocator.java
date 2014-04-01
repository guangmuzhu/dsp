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

package com.delphix.platform;

/**
 * This class provides static access to the PlatformManager.
 */
public class PlatformManagerLocator {
    // Spring injected members
    private static PlatformManager platformManager;

    public void setPlatformManager(PlatformManager platformManager) {
        PlatformManagerLocator.platformManager = platformManager;
    }

    public static LogLevelStrategy getLogLevelStrategy() {
        return PlatformManager.getLogLevelStrategy();
    }

    public static InterruptStrategy getInterruptStrategy() {
        return platformManager.getInterruptStrategy();
    }

    public static UUIDStrategy getUUIDStrategy() {
        return platformManager.getUUIDStrategy();
    }
}
