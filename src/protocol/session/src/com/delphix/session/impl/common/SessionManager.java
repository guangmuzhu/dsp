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

package com.delphix.session.impl.common;

import com.delphix.appliance.logger.Logger;
import com.delphix.session.impl.sasl.SessionSaslProvider;
import com.delphix.session.util.EventManager;
import com.delphix.session.util.ExecutorUtil;
import com.delphix.session.util.ProtocolVersion;

import java.util.concurrent.*;

import static com.delphix.session.impl.common.SessionProtocol.V_1_0_0;

public abstract class SessionManager {

    protected static final Logger logger = Logger.getLogger(SessionManager.class);

    protected static final int CORE_SCHEDULER_THREADS = 2; // Core number of scheduler threads

    protected EventManager eventManager; // Session event manager
    protected ExecutorService executionManager; // Session execution manager
    protected ScheduledExecutorService scheduleManager; // Session schedule manager

    protected SessionTransportManager channelManager; // Transport channel manager

    protected final ProtocolVersion minVersion; // Minimum protocol version supported
    protected final ProtocolVersion maxVersion; // Maximum protocol version supported

    static {
        // Install the delphix-sasl provider dynamically
        SessionSaslProvider.install();
    }

    public SessionManager() {
        minVersion = V_1_0_0;
        maxVersion = V_1_0_0;
    }

    public void start() {
        // Fire up the event manager
        eventManager = new EventManager();

        // Fire up the lifecycle manager
        scheduleManager = Executors.newScheduledThreadPool(CORE_SCHEDULER_THREADS);

        // Fire up the execution manager
        executionManager = Executors.newCachedThreadPool();
    }

    public void stop() {
        // Shutdown the transport manager
        channelManager.shutdown();

        // Shutdown the execution manager
        shutdown(executionManager);

        // Shutdown the lifecycle manager
        shutdown(scheduleManager);

        // Shutdown the event manager
        shutdown(eventManager);
    }

    private void shutdown(ExecutorService service) {
        try {
            ExecutorUtil.shutdown(service);
        } catch (TimeoutException e) {
            logger.errorf("failed to shutdown executor service %s", service);
        }
    }

    public SessionTransportManager getChannelManager() {
        return channelManager;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public ExecutorService getExecutionManager() {
        return executionManager;
    }

    public ScheduledExecutorService getScheduleManager() {
        return scheduleManager;
    }

    public ProtocolVersion getMaxVersion() {
        return maxVersion;
    }

    public ProtocolVersion getMinVersion() {
        return minVersion;
    }

    public ScheduledFuture<?> schedule(Runnable task, long delay) {
        return scheduleManager.schedule(task, delay, TimeUnit.MILLISECONDS);
    }

    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        return scheduleManager.schedule(task, delay, unit);
    }

    public void execute(Runnable task) {
        executionManager.execute(task);
    }
}
