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

import com.delphix.appliance.logger.Logger;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.*;

/**
 * This class describes the event manager. The event manager guarantees orderly delivery of events. Specifically,
 * the events are posted to the listeners in the same order as they are posted by the sources. It also supports
 * flushing events from the same source which may be used before the source ceases to exist. Events are always
 * processed in separate contexts from those posting them.
 *
 * The EventExecutor is both an executor and a runnable at the same time. It is an executor to execute the events
 * posted to the event manager. It is a runnable so that it can be processed in the context of a pool thread in
 * the event manager. There is one event executor for each event source to ensure ordered delivery of events. The
 * following is the processing flow of events.
 *
 *  EventManager -> EventExecutor -> EventManager (ThreadPool)
 *
 * The event manager manages a collection of event executors and provides the execution context support while the
 * event executor provides ordering.
 *
 * The event manager also processes non-event tasks since it is also a thread pool executor. There is no ordering
 * guarantee made with non-event tasks.
 */
public class EventManager extends ThreadPoolExecutor {

    private static final Logger logger = Logger.getLogger(EventManager.class);

    // Event manager parameters
    private static final int DEFAULT_EVENT_THREADS = 1;

    // Executor map keyed by the event source (null key unsupported)
    private final ConcurrentMap<EventSource, Executor> executors = new ConcurrentHashMap<EventSource, Executor>();

    // Default executor for null source
    private final EventExecutor executor = new EventExecutor();

    /**
     * Create event manager with default number of threads.
     */
    public EventManager() {
        this(DEFAULT_EVENT_THREADS);
    }

    /**
     * Create event manager with the specified number of threads.
     */
    public EventManager(int corePoolSize) {
        /*
         * Using an unbounded queue (for example a LinkedBlockingQueue without a predefined capacity) will cause new
         * tasks to wait in the queue when all corePoolSize threads are busy. Thus, no more than corePoolSize threads
         * will ever be created. (And the value of the maximumPoolSize therefore doesn't have any effect.)
         */
        super(corePoolSize, Integer.MAX_VALUE, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
    }

    /**
     * Create thread pool with event manager style execution.
     */
    protected EventManager(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    @Override
    public void execute(Runnable runnable) {
        if (!(runnable instanceof Event)) {
            super.execute(runnable);
            return;
        }

        Event event = (Event) runnable;
        Executor executor = getEventExecutor(event);

        executor.execute(event);
    }

    @Override
    public void afterExecute(Runnable runnable, Throwable t) {
        super.afterExecute(runnable, t);

        if (!(runnable instanceof EventExecutor)) {
            return;
        }

        EventExecutor executor = (EventExecutor) runnable;
        EventSource source = executor.getSource();

        if (source != null && source.isTerminal()) {
            executors.remove(source);
        }
    }

    private Executor getEventExecutor(Event event) {
        EventSource source = event.getSource();

        if (source == null) {
            return executor;
        }

        Executor executor = executors.get(source);

        if (executor == null) {
            executor = new EventExecutor(source);

            Executor current = executors.putIfAbsent(source, executor);

            if (current != null) {
                executor = current;
            }
        }

        return executor;
    }

    public void flush() {
        flush(null, false);
    }

    public void flush(EventSource source, boolean terminal) {
        EventFlusher event = new EventFlusher(source);

        execute(event);
        event.awaitDone();

        if (terminal) {
            executors.remove(source);
        }
    }

    private class EventFlusher extends Event {

        private boolean done;

        public EventFlusher(EventSource source) {
            super(source);
        }

        public synchronized void awaitDone() {
            while (!done) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    // Do nothing
                }
            }
        }

        @Override
        public synchronized void run() {
            done = true;
            notify();
        }
    }

    private void doExecute(Runnable runnable) {
        super.execute(runnable);
    }

    private class EventExecutor implements Executor, Runnable {

        private final Queue<Event> events = new LinkedList<Event>();
        private final EventSource source;

        public EventExecutor() {
            this(null);
        }

        public EventExecutor(EventSource source) {
            this.source = source;
        }

        public EventSource getSource() {
            return source;
        }

        @Override
        public void execute(Runnable runnable) {
            Event event = (Event) runnable;
            boolean idle;

            synchronized (this) {
                idle = events.isEmpty();
                events.add(event);
            }

            if (idle) {
                // Process the event executor in the context of a pool thread
                doExecute(this);
            }
        }

        @Override
        public void run() {
            for (;;) {
                Event event;

                synchronized (this) {
                    event = events.peek();
                }

                try {
                    event.run();
                } catch (Throwable t) {
                    logger.errorf(t, "failed to process event");
                }

                synchronized (this) {
                    events.remove();

                    if (events.isEmpty()) {
                        break;
                    }
                }
            }
        }
    }
}
