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

import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;

/**
 * This class describes a single-threaded asynchronous work queue. It is implemented using the executor service and
 * the future task. The task is fired up when the work queue becomes ready and it is stopped otherwise. There is at
 * most one instance of the task running at any moment. Hence, work queue processing is serialized.
 */
public abstract class AsyncTask<T> {

    protected final ExecutorService executor;

    protected final Queue<T> queue;
    protected final Object lock;

    protected FutureTask<?> task;

    protected final Runnable runnable = new Runnable() {

        @Override
        public void run() {
            processTask();
        }
    };

    public AsyncTask(Queue<T> queue, Object lock, ExecutorService executor) {
        this.executor = executor;

        this.queue = queue;
        this.lock = lock;

        // Initialize the future task with an initial submission followed by quiescing
        task = new FutureTask<Object>(new Runnable() {

            @Override
            public void run() {
                // Do nothing
            }
        }, null);

        executor.execute(task);

        finish();
    }

    /**
     * Task submission must be invoked with the lock to serialize with the running task.
     */
    public boolean submit() {
        assert Thread.holdsLock(lock);

        if (queue.isEmpty() || !isDone() || !isReady()) {
            return false;
        }

        task = new FutureTask<Object>(runnable, null) {

            @Override
            protected void done() {
                // Reschedule the task if it has become ready again since it exited the process loop
                synchronized (lock) {
                    if (!queue.isEmpty() && isReady()) {
                        submit();
                    }
                }
            }
        };

        executor.execute(task);

        return true;
    }

    public boolean submit(T work) {
        queue.offer(work);
        return submit();
    }

    public void finish() {
        for (;;) {
            try {
                task.get();
                break;
            } catch (InterruptedException e) {
                // Do nothing
            } catch (ExecutionException e) {
                break;
            }
        }
    }

    public boolean isDone() {
        return task.isDone();
    }

    protected void processTask() {
        for (;;) {
            T work;

            synchronized (lock) {
                if (!isReady()) {
                    break;
                }

                work = queue.poll();

                if (work == null) {
                    break;
                }
            }

            doWork(work);
        }
    }

    /**
     * Check if the work queue is ready.
     */
    protected boolean isReady() {
        return true;
    }

    /**
     * Work processor.
     */
    protected abstract void doWork(T work);
}
