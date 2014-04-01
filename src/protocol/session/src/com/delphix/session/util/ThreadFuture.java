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

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

public class ThreadFuture<V> extends AbstractFuture<V> {

    protected Callable<V> callable;
    protected Thread thread;

    public ThreadFuture(Callable<V> callable) {
        this(callable, false);
    }

    public ThreadFuture(Callable<V> callable, boolean isRecurring) {
        super(isRecurring);
        this.callable = callable;
    }

    public ThreadFuture(Runnable runnable) {
        this(runnable, null);
    }

    public ThreadFuture(Runnable runnable, boolean isRecurring) {
        this(runnable, null, isRecurring);
    }

    public ThreadFuture(Runnable runnable, V result) {
        this(Executors.callable(runnable, result));
    }

    public ThreadFuture(Runnable runnable, V result, boolean isRecurring) {
        this(Executors.callable(runnable, result), isRecurring);
    }

    private synchronized void doCancel() {
        // The thread is null if it is cancelled before even started
        if (thread == null) {
            thread = Thread.currentThread();
            return;
        }

        // Interrupt the task runner
        PlatformManagerLocator.getInterruptStrategy().interrupt(thread);
    }

    @Override
    protected void doCancel(boolean mayInterruptIfRunning) {
        if (!mayInterruptIfRunning) {
            return;
        }

        doCancel();
    }

    protected synchronized void beforeRun() throws InterruptedException {
        // The thread must be null unless the task has been cancelled
        if (thread != null) {
            throw new InterruptedException();
        }

        // Set the task runner for cancellation
        thread = Thread.currentThread();
    }

    /**
     * In case of cancellation, the interrupt may or may not have been picked up by the callable. So the task runner
     * may carry the interrupted status upon exit. However, the ThreadPoolExecutor ensures that interrupt status is
     * always cleared before task run. So we don't need to worry about clearing it here.
     */
    protected synchronized void afterRun() {
        assert thread == Thread.currentThread();

        // Reset the task runner
        thread = null;
    }

    @Override
    protected void doRun() throws Exception {
        V result;

        beforeRun();

        try {
            result = callable.call();
        } finally {
            afterRun();
        }

        setResult(result);
    }
}
