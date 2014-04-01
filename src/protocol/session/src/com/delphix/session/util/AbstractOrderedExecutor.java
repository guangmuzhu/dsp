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

import com.delphix.session.service.ServiceFuture;
import com.delphix.session.service.ServiceRequest;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

/**
 * The class provides ordered execution of service requests by the source from which they are originated. Requests
 * that are associated with the same source are guaranteed to be executed in the order of submission end-to-end.
 * However, execution order is not guaranteed for requests from different sources because that is not needed and
 * enforcing it may slow things down through reduced scalability.
 *
 * The ordered executor may be used to support multiple data streams over a single session among other things. The
 * subclass must override the getSource() method to identify the source of a given request.
 */
public abstract class AbstractOrderedExecutor extends EventManager {

    /**
     * Create a cached ordered executor using the direct handoff model.
     */
    public AbstractOrderedExecutor() {
        super(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
    }

    /**
     * Create a fixed size ordered executor using the unbounded queue model.
     */
    public AbstractOrderedExecutor(int corePoolSize) {
        super(corePoolSize, corePoolSize, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
    }

    /**
     * Create an ordered executor with support of the generic bounded queue model.
     */
    public AbstractOrderedExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    @Override
    public void execute(Runnable runnable) {
        if (!(runnable instanceof ServiceFuture)) {
            super.execute(runnable);
            return;
        }

        ServiceFuture future = (ServiceFuture) runnable;
        ServiceRequest request = future.getServiceRequest();

        EventSource source = getSource(request);

        if (source != null) {
            runnable = new CommandEvent(future, source);
        }

        super.execute(runnable);
    }

    /**
     * Return the source associated with the given request.
     */
    protected abstract EventSource getSource(ServiceRequest request);

    protected class CommandEvent extends Event {

        private final ServiceFuture future;
        private final EventSource source;

        public CommandEvent(ServiceFuture future, EventSource source) {
            this.future = future;
            this.source = source;
        }

        @Override
        public EventSource getSource() {
            return source;
        }

        @Override
        public void run() {
            future.run();
        }
    }
}
