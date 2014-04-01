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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class provides an abstract implementation of a data receiver task. It behaves as a data sink by exposing a
 * gathering write interface through which the data from the sender can be made available. It then reads the data
 * from the other end of the data sink from a separate context associated with the receiver until the end of data
 * is reached. A subclass is responsible for the construction of the data pipe as well as how to process the data
 * read.
 */
public abstract class AbstractDataReceiver<V> implements Callable<V> {

    protected static final Logger logger = Logger.getLogger(AbstractDataReceiver.class);

    // Data source parameters
    protected WritableByteChannel channel;

    // Debug output stream (optional)
    protected FileOutputStream debug;

    // Data offset
    protected long offset;

    // Receiver complete (set when the final update request has been received)
    protected boolean complete;

    // Async task future
    protected AsyncFuture<V> future;

    // Atomic task exception
    protected final AtomicReference<Throwable> exception;

    protected AbstractDataReceiver() {
        exception = new AtomicReference<Throwable>();
    }

    public void setupDebug() {
        File file = getDebugFile();

        try {
            debug = new FileOutputStream(file);
        } catch (IOException e) {
            logger.errorf(e, "Failed to create debug output %s", file.getName());
        }
    }

    @Override
    public V call() throws Exception {
        V value = null;

        try {
            value = receive();

            // Wait for final update
            awaitDone();
        } catch (Throwable t) {
            handleException(t);
        } finally {
            finish();
        }

        return value;
    }

    /**
     * Receive the data written down the channel from the other end.
     */
    protected abstract V receive();

    /**
     * Interrupt the data receiver. The data receiver is running in a thread context. Normally, to cancel the task,
     * all one has to do is interrupt the thread. But in some cases, the thread may not be interruptible. The
     * subclass may override this method to do what is necessary to stop the thread.
     */
    public void interrupt() {

    }

    /**
     * Handle the exception encountered during the receive process.
     */
    protected void handleException(Throwable t) {
        if (!exception.compareAndSet(null, t)) {
            t = exception.get();
        }

        throw ExceptionUtil.getDelphixException(t);
    }

    /**
     * Close the channel for writing.
     */
    protected void finish() {
        ExceptionUtil.closeIgnoreExceptions(channel);

        if (debug != null) {
            ExceptionUtil.closeIgnoreExceptions(debug);
        }
    }

    /**
     * Wait for the final update to complete the receiver task.
     */
    protected synchronized void awaitDone() {
        while (!complete) {
            try {
                wait();
            } catch (InterruptedException e) {
                throw new DelphixInterruptedException();
            }
        }
    }

    /**
     * Inform the receiver of the final update to conclude the receiver task.
     */
    public synchronized void complete() {
        complete = true;
        notify();
    }

    /**
     * Write all the data remaining in the byte buffer array down the channel. It will be picked up from the other end
     * by the receiver task. In case of exception, the channel is closed for writing and the receiver task cancelled
     * in a best-effort if not terminated already. The receiver exception is thrown instead of the writer exception.
     */
    public long write(long offset, ByteBuffer[] srcs) {
        long bytesWritten;

        try {
            if (debug != null) {
                ByteBufferUtil.writeFully(debug, ByteBufferUtil.duplicate(srcs));
            }

            bytesWritten = ByteBufferUtil.writeFully(channel, srcs);

            assert this.offset == offset : "offset mismatch";
            this.offset += bytesWritten;
        } catch (Throwable t) {
            logger.errorf(t, "failed to write to receiver");

            // Use the writer exception unless it has been set
            exception.compareAndSet(null, t);

            // Cancel the receiver with an interruptible attempt
            future.cancel(true, true, true);

            if (future.isDone()) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    t = ExceptionUtil.unwrap(e);
                } catch (InterruptedException e) {
                    assert false : "unexpected interrupt when task is done";
                }
            }

            throw ExceptionUtil.getDelphixException(t);
        }

        return bytesWritten;
    }

    /**
     * Return the file to use as the debug output.
     */
    protected abstract File getDebugFile();

    public AsyncFuture<V> getFuture() {
        return future;
    }

    public void setFuture(AsyncFuture<V> future) {
        this.future = future;
    }
}
