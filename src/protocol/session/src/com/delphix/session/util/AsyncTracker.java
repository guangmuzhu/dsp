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
import com.delphix.appliance.server.exception.DelphixInterruptedException;
import com.delphix.appliance.server.util.ExceptionUtil;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;

/**
 * This class provides the functionality needed for executing and tracking the status of a collection of asynchronous
 * tasks. A typical consumer of the async task tracker is a higher level task that initiates one or more async sub-
 * tasks during its execution. The higher level task may use the tracker for sub-task completion, cancellation, and
 * exception handling.
 */
public class AsyncTracker {

    protected static final Logger logger = Logger.getLogger(AsyncTracker.class);

    // In-flight task map
    protected final Map<Object, AsyncFuture<?>> activeMap;

    // Completed task queue
    protected final Queue<AsyncFuture<?>> doneQueue;

    // Task exception queue
    protected final Queue<Throwable> exceptionQueue;

    // Async task done callback (optional)
    protected final Runnable callback;

    // Async task result processor (optional)
    protected final AsyncResultProcessor processor;

    // Maximum number of tasks to be tracked
    protected final int maxTasks;

    // Wait for the tracker to complete
    protected boolean waitDone;

    public AsyncTracker() {
        this(Integer.MAX_VALUE);
    }

    public AsyncTracker(int maxTasks) {
        this(null, null, maxTasks);
    }

    public AsyncTracker(AsyncResultProcessor processor) {
        this(null, processor, Integer.MAX_VALUE);
    }

    public AsyncTracker(Runnable callback) {
        this(callback, null, Integer.MAX_VALUE);
    }

    public AsyncTracker(Runnable callback, AsyncResultProcessor processor, int maxTasks) {
        this.callback = callback;
        this.maxTasks = maxTasks;
        this.processor = processor;

        activeMap = new HashMap<Object, AsyncFuture<?>>();
        exceptionQueue = new LinkedList<Throwable>();

        // This is concurrent to avoid synchronization when only offering to/polling from it
        doneQueue = new ConcurrentLinkedQueue<AsyncFuture<?>>();
    }

    /**
     * Add the async task and the corresponding async future to the outstanding task map for tracking purpose. The map
     * allows us to abort all outstanding tasks in case the higher level task that owns the tracker must be cancelled.
     * Process the completed task queue before returning and report any exception encountered so far.
     */
    public void track(Object task, AsyncFuture<?> future) {
        boolean done;

        synchronized (this) {
            // No new tasks allowed once the tracker is waiting to be shutdown
            assert !waitDone : "tracker already shutdown";

            done = future.isDone();

            /*
             * In case of a race with the completion context, the task is only added to the map if the future has not
             * yet completed.
             */
            if (!done) {
                activeMap.put(task, future);
            }

            // Block the tracker if the maximum task limit has been reached
            while (activeMap.size() >= maxTasks) {
                logger.tracef("maximum task limit %d has been reached", maxTasks);

                try {
                    wait();
                } catch (InterruptedException e) {
                    throw new DelphixInterruptedException();
                }
            }
        }

        if (done) {
            doneQueue.offer(future);
        }

        // Process the completed task queue
        complete();
    }

    /**
     * This is the portion of the async task completion callback that updates various internal tracker states and
     * therefore must be done with synchronization.
     */
    private synchronized boolean taskDone(Object task) {
        AsyncFuture<?> future = activeMap.get(task);

        /*
         * It is possible this may race with the tracker context such that the task hasn't been added to the
         * command map by the time it is already completed, in which case, get would return null and task
         * completion is processed immediately after dispatch in the tracker context.
         */
        if (future == null) {
            return false;
        }

        // Limit notify callback to empty to non-empty transition only
        boolean notify = doneQueue.isEmpty();

        assert future.isDone();
        doneQueue.offer(future);

        // Finally remove the task from the active map
        int numTasks = activeMap.size();

        activeMap.remove(task);

        if (waitDone || numTasks == maxTasks) {
            notifyAll();
        }

        return notify;
    }

    /**
     * Async task completion callback. This is invoked after the async task has completed, i.e., the corresponding
     * async future is done, from within the completion context.
     */
    public void done(Object task) {
        boolean notify = taskDone(task);

        if (notify && callback != null) {
            callback.run();
        }
    }

    /**
     * Complete the async task on the done queue. This is invoked from the tracker context for purpose of task result
     * processing and exception handling.
     */
    public void complete() {
        AsyncFuture<?> done;

        while (true) {
            done = doneQueue.poll();

            /*
             * In the async task completion callback, we will notify the tracker context only when the doneQueue
             * goes through an empty to non-empty transition. It is possible that we processed all the completed
             * async tasks here after the doneQueue is found to be non-empty and before a newly completed task is
             * offered to the doneQueue from the completion callback context. If we were to simply bail here, we
             * would be stuck for good waiting for a notification that would never arrive. To avoid this race, we
             * check the doneQueue again but this time synchronized with the completion callback context. We will
             * continue if the latter has added more tasks to the doneQueue.
             */
            if (done == null) {
                synchronized (this) {
                    if (doneQueue.isEmpty()) {
                        break;
                    }
                }

                continue;
            }

            Object obj = null;

            try {
                obj = done.get();
            } catch (InterruptedException e) {
                logger.errorf(e, "Unexpected interrupted exception in tracker during complete");
                assert false;
            } catch (CancellationException e) {
                logger.errorf(e, "async task cancelled");
                exceptionQueue.add(e);
            } catch (ExecutionException e) {
                logger.errorf(e, "async task failed");
                exceptionQueue.add(ExceptionUtil.unwrap(e));
            }

            // Post process the task result if necessary
            try {
                if (processor != null) {
                    processor.process(done);
                } else if (obj instanceof AsyncResult) {
                    AsyncResult result = (AsyncResult) obj;
                    result.process();
                }
            } catch (Throwable t) {
                exceptionQueue.add(t);
            }
        }

        // Handle exceptions and discard the remaining ones
        try {
            handleException();
        } finally {
            exceptionQueue.clear();
        }
    }

    /**
     * Handle async task exceptions. This is invoked at the end of the async task completion processing. By default,
     * the first exception encountered since the last run is thrown. Override this method to change the behavior.
     */
    protected void handleException() {
        Throwable t = exceptionQueue.peek();

        if (t != null) {
            throw ExceptionUtil.getDelphixException(t);
        }
    }

    /**
     * Attempt to cancel all outstanding async tasks. This call may block while waiting for the tasks to complete.
     * Interrupt is ignored to ensure nothing is left behind. It may introduce a delay but the wait should never be
     * indefinite.
     */
    public void cancel() {
        logger.debug("async tracker cancellation requested");

        List<AsyncFuture<?>> futures = new ArrayList<AsyncFuture<?>>();

        synchronized (this) {
            futures.addAll(activeMap.values());
        }

        for (AsyncFuture<?> future : futures) {
            future.cancel(true);
        }

        synchronized (this) {
            waitDone = true;

            while (!activeMap.isEmpty()) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    logger.errorf(e, "async tracker interrupted - active tasks %d", activeMap.size());
                }
            }
        }

        // Process the completed task queue
        try {
            complete();
        } catch (Throwable t) {
            logger.errorf(t, "exception encountered during async task processing");
        }
    }

    /**
     * Wait for all outstanding async tasks to complete. This is invoked after all async tasks have been issued. One
     * or more async tasks may fail or the caller may be interrupted, in which case, the caller may bail out early
     * before the remaining tasks complete and it should always call the cancel() method for cleanup.
     */
    public void awaitDone() {
        synchronized (this) {
            waitDone = true;
        }

        while (true) {
            synchronized (this) {
                // Stop if there are no more active tasks left
                if (activeMap.isEmpty()) {
                    break;
                }

                // Wait until at least one active task has completed
                while (doneQueue.isEmpty()) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        logger.errorf(e, "async tracker interrupted - active tasks %d", activeMap.size());
                        throw new DelphixInterruptedException();
                    }
                }
            }

            // Process the completed task queue
            complete();
        }

        /*
         * Process the completed task queue for one last time. We exit the while loop above when there are no more
         * active tasks left. When all remaining tasks are done before we check the active map, those tasks will be
         * placed in the done queue and waiting to be completed.
         */
        complete();
    }

    /**
     * Check if the tracker is done.
     */
    public synchronized boolean isDone() {
        return waitDone && activeMap.isEmpty() && doneQueue.isEmpty();
    }

    /**
     * Check if the tracker is being shut down.
     */
    public synchronized boolean isWaitDone() {
        return waitDone;
    }
}
