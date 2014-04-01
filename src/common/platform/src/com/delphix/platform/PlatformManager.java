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
 * This class provides platform-independent APIs to facilitate the creation of modules which can be use outside of
 * the management service.
 */
public class PlatformManager {
    /*
     * In the context of the management service, this field is "injected" with an alternative strategy by
     * LogLevelStrategyConfigurator on stack startup. For more information, see the comment in that class.
     */
    private static LogLevelStrategy logLevelStrategy = new LogLevelStrategy() {
        @Override
        public void registerFQCN(String FQCN) {
            // Do nothing by default outside the stack.
        }
    };

    private InterruptStrategy interruptStrategy;
    private UUIDStrategy uuidStrategy;

    public static LogLevelStrategy getLogLevelStrategy() {
        return logLevelStrategy;
    }

    public static void setLogLevelStrategy(LogLevelStrategy logLevelStrategy) {
        PlatformManager.logLevelStrategy = logLevelStrategy;
    }

    public InterruptStrategy getInterruptStrategy() {
        return interruptStrategy;
    }

    public void setInterruptStrategy(InterruptStrategy interruptStrategy) {
        this.interruptStrategy = interruptStrategy;
    }

    public UUIDStrategy getUUIDStrategy() {
        return uuidStrategy;
    }

    public void setUUIDStrategy(UUIDStrategy uuidStrategy) {
        this.uuidStrategy = uuidStrategy;
    }
}
