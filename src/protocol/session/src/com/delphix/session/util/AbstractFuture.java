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

import com.delphix.appliance.logger.Logger;
import com.delphix.platform.PlatformManagerLocator;

import java.io.InterruptedIOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This class describes a future implementation. It is different from the default java FutureTask with regard to the
 * cancellation semantics. Specifically,
 *
 *   - The done callback, usually overridden in a subclass to provide task notification support, is not called until
 *   the task is fully quiesced, i.e., it has either completed or shall never be started again. In the standard java
 *   implementation, the done callback is invoked immediately from the cancellation context regardless of the task
 *   state.
 *
 *   - The default cancel interface inherited from Future<V> is made synchronous. It waits uninterruptibly until the
 *   task is done. That is different from the standard java implementation which returns immediately even though the
 *   task thread may still be running.
 *
 *   - Asynchronous cancel interfaces are added which return true to indicate cancellation has been initiated.
 *
 *   - If a task is completed anyways after the cancellation has been initiated, it will complete, i.e., isDone is
 *   true and isCancelled false. That is also different from the standard java implementation which sets both to
 *   true after cancel returns.
 */
public abstract class AbstractFuture<V> implements AsyncFuture<V> {

    private static final Logger logger = Logger.getLogger(AbstractFuture.class);

    protected AsyncFutureState state;
    protected final boolean isRecurring;

    protected V result;
    protected Throwable exception;

    public AbstractFuture() {
        this(false);
    }

    public AbstractFuture(boolean isRecurring) {
        setState(AsyncFutureState.INITIAL);
        this.isRecurring = isRecurring;
    }

    protected void done() {
        // Do nothing
    }

    protected void setState(AsyncFutureState newState) {
        AsyncFutureState oldState = state;

        AsyncFutureState.validate(oldState, newState);
        state = newState;

        logger.tracef("%s: state transition %s -> %s", this, oldState, newState);
    }

    /**
     * Performs the final state transitions when a task has either finished executing or an exception was thrown.
     */
    private void finalizeState(boolean interrupted) {
        if (interrupted && state == AsyncFutureState.ABORTING) {
            setState(AsyncFutureState.ABORTED);
        } else if (isRecurring && state == AsyncFutureState.ABORTING) {
            setState(AsyncFutureState.COMPLETED);
        } else if (isRecurring) {
            setState(AsyncFutureState.COMPLETED);
            setState(AsyncFutureState.INITIAL);
        } else {
            setState(AsyncFutureState.COMPLETED);
        }
    }

    public void setResult(V v) {
        synchronized (this) {
            finalizeState(false);
            result = v;

            notifyAll();
        }
        done();
    }

    public void setException(Throwable t) {
        boolean interrupted = isInterrupt(t);

        if (!interrupted) {
            logger.debug(t, "Encountered the following exception in a future: ");
        }

        synchronized (this) {
            finalizeState(interrupted);

            if (!interrupted && isDone()) {
                exception = t;
            } else if (!isDone()) {
                exception = null;
            }

            notifyAll();
        }
        done();
    }

    protected boolean isInterrupt(Throwable t) {
        do {
            if (t instanceof InterruptedException || t instanceof InterruptedIOException) {
                return true;
            }

            t = t.getCause();
        } while (t != null);

        return false;
    }

    protected abstract void doCancel(boolean mayInterruptIfRunning);

    @Override
    public boolean cancel(boolean mayInterruptIfRunning, boolean waitUntilDone, boolean interruptible) {
        boolean aborted = false;

        synchronized (this) {
            if (state == AsyncFutureState.INITIAL) {
                setState(AsyncFutureState.ABORTED);
                aborted = true;
                notifyAll();
            } else if (state == AsyncFutureState.ACTIVE) {
                setState(AsyncFutureState.ABORTING);
            } else {
                return false;
            }
        }

        if (aborted) {
            done();
            return true;
        }

        doCancel(mayInterruptIfRunning);

        if (!waitUntilDone) {
            return true;
        }

        synchronized (this) {
            while (!taskDone()) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    if (interruptible) {
                        return false;
                    }
                }
            }
        }

        return isCancelled();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning, boolean waitUntilDone) {
        return cancel(mayInterruptIfRunning, waitUntilDone, false);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return cancel(mayInterruptIfRunning, true);
    }

    protected abstract void doRun() throws Exception;

    protected void work() {
        try {
            doRun();
        } catch (Throwable t) {
            setException(t);
        }
    }

    @Override
    public void run() {
        synchronized (this) {
            if (state != AsyncFutureState.INITIAL) {
                return;
            }

            setState(AsyncFutureState.ACTIVE);
        }

        work();
    }

    @Override
    public synchronized boolean isCancelled() {
        return state == AsyncFutureState.ABORTED;
    }

    @Override
    public synchronized boolean isDone() {
        return taskDone();
    }

    protected boolean taskDone() {
        return state == AsyncFutureState.COMPLETED || state == AsyncFutureState.ABORTED;
    }

    @Override
    public synchronized V get() throws InterruptedException, ExecutionException {
        while (!taskDone()) {
            wait();
        }

        if (state == AsyncFutureState.ABORTED) {
            throw new CancellationException();
        }

        if (exception != null) {
            throw new ExecutionException(exception);
        }

        return result;
    }

    @Override
    public synchronized V get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        long until = System.currentTimeMillis() + unit.toMillis(timeout);

        while (!taskDone()) {
            long timeoutMs = until - System.currentTimeMillis();

            if (timeoutMs > 0) {
                wait(timeoutMs);
            } else {
                throw new TimeoutException();
            }
        }

        if (state == AsyncFutureState.ABORTED) {
            throw new CancellationException();
        }

        if (exception != null) {
            throw new ExecutionException(exception);
        }

        return result;
    }

    @Override
    public V await() throws ExecutionException {
        V result = null;

        try {
            result = get();
        } catch (InterruptedException t) {
            boolean cancelled = false;

            // Cancel the task and synchronously wait for it to complete
            cancel(true);

            /*
             * Try to get the result again after task is complete. If the task has completed successfully or failed
             * with an execution exception just before it is cancelled, we would rather return that than the less
             * informative interrupted exception.
             */
            try {
                result = get();
            } catch (InterruptedException e) {
                assert false;
            } catch (CancellationException e) {
                cancelled = true;
                throw e;
            } finally {
                // Restore the interrupt to be picked up later in case the task has completed on its own
                if (!cancelled) {
                    PlatformManagerLocator.getInterruptStrategy().interrupt(Thread.currentThread());
                }
            }
        }

        return result;
    }

    @Override
    public synchronized V await(long timeout, TimeUnit unit) throws ExecutionException, TimeoutException {
        V result = null;

        try {
            result = get(timeout, unit);
        } catch (InterruptedException t) {
            boolean cancelled = false;

            // Cancel the task and synchronously wait for it to complete
            cancel(true);

            /*
             * Try to get the result again after task is complete. If the task has completed successfully or failed
             * with an execution exception just before it is cancelled, we would rather return that than the less
             * informative interrupted exception.
             */
            try {
                result = get();
            } catch (InterruptedException e) {
                assert false;
            } catch (CancellationException e) {
                cancelled = true;
                throw e;
            } finally {
                // Restore the interrupt to be picked up later in case the task has completed on its own
                if (!cancelled) {
                    PlatformManagerLocator.getInterruptStrategy().interrupt(Thread.currentThread());
                }
            }
        }

        return result;
    }
}
