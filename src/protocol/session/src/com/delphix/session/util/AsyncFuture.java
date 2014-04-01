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
 * Copyright (c) 2013, 2014 by Delphix. All rights reserved.
 */

package com.delphix.session.util;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This interface describes an asynchronously cancellable future. It augments the synchronous cancel interface found
 * in Future. See interface descriptions below for details. An implementation of this interface is provided in the
 * AbstractFuture class, which further details the semantic differences with the standard Java FutureTask.
 */
public interface AsyncFuture<V> extends RunnableFuture<V> {

    /**
     * Attempt to cancel execution of this task. This attempt will fail if the task has already completed, has already
     * been cancelled, or could not be cancelled for some other reason. If successful, and this task has not started
     * when cancel is called, this task should never run. If the task has already started, then the
     * mayInterruptIfRunning parameter determines whether the thread executing this task should be interrupted in an
     * attempt to stop the task.
     *
     * If waitUntilDone is set, the caller will block wait until the task is done.
     *
     * If waitUntilDone is not set, after this method returns, subsequent calls to isDone() may _not_ return true and
     * subsequent calls to isCancelled() may not return true even if this method returned true. The caller must call
     * get() or rely on the done() callback to determine the task state.
     */
    public boolean cancel(boolean mayInterruptIfRunning, boolean waitUntilDone, boolean interruptible);

    /**
     * Similar to above except the wait is uninterruptible.
     */
    public boolean cancel(boolean mayInterruptIfRunning, boolean waitUntilDone);

    /**
     * Wait for the async future to complete and retrieve the result. Upon return from this method, the future is
     * guaranteed to have terminated. If the future has completed on its own, either the result is returned or an
     * execution exception is thrown, depending on the completion status. Otherwise, a CancellationException is
     * thrown if the future is cancelled. If the waiting thread is interrupted, it will synchronously cancel the
     * future.
     */
    public V await() throws ExecutionException;

    /**
     * Same as above except with timeout.
     */
    public V await(long timeout, TimeUnit unit) throws ExecutionException, TimeoutException;
}
