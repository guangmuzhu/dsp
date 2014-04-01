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
import com.delphix.appliance.server.util.ExceptionUtil;
import com.delphix.session.service.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static com.delphix.session.service.ServiceOption.FORE_MAX_REQUEST;
import static com.delphix.session.service.ServiceOption.PAYLOAD_COMPRESS;

/**
 * This class provides an abstract implementation of a data sender task. It reads data from a data source sequentially
 * and writes it out to the remote site connected via a service nexus. The task supports cancellation via interrupt
 * sent to the executing thread. In case an exception is encountered during task processing, the task is responsible
 * for all necessary cleanup, such as closing the data source and canceling the outstanding service requests. The
 * exception that caused the task to terminate may be retrieved later via the corresponding async future.
 */
public abstract class AbstractDataSender implements Runnable {

    protected static final Logger logger = Logger.getLogger(AbstractDataSender.class);

    // Data source
    protected final DataSource source;

    // Service nexus (nexus.syncDispatch.local set to true)
    protected final ServiceNexus nexus;

    // Async task tracker
    protected final AsyncTracker tracker;

    // Debug output stream (optional)
    protected FileOutputStream debug;

    // Bytes expected from data source each read
    protected int bytesToRead;

    // Total bytes desired
    protected long bytesWanted;

    // Bytes left to send and total bytes sent
    protected long bytesLeft;
    protected long bytesSent;

    // Send EOF upon failure to read from the source
    protected boolean eofOnFailure;

    // Ignore EOF
    protected boolean eofIgnore;

    // Atomic task exception
    protected final AtomicReference<Throwable> exception;

    protected AbstractDataSender(DataSource source, ServiceNexus nexus) {
        this.source = source;
        this.nexus = nexus;

        tracker = new AsyncTracker(new SendResultProcessor());
        exception = new AtomicReference<Throwable>();
    }

    public void setBytesWanted(long bytesWanted) {
        this.bytesWanted = bytesWanted;
    }

    public long getBytesSent() {
        return bytesSent;
    }

    public void setEofOnFailure(boolean eofOnFailure) {
        this.eofOnFailure = eofOnFailure;
    }

    public void setEofIgnore(boolean eofIgnore) {
        this.eofIgnore = eofIgnore;
    }

    private ByteBuffer[] read(int length) throws IOException {
        ByteBuffer[] data = null;

        try {
            data = source.read(length);
        } catch (IOException e) {
            logger.debugf(e, "failed to read from data source %s", source);

            if (!eofOnFailure) {
                throw e;
            }
        }

        return data;
    }

    public void setupDebug() {
        File file = getDebugFile();

        try {
            debug = new FileOutputStream(file);
        } catch (IOException e) {
            logger.errorf(e, "failed to create debug output %s", file.getName());
        }
    }

    @Override
    public void run() {
        try {
            send();
        } catch (Throwable t) {
            handleException(t);
        } finally {
            finish();
        }
    }

    /**
     * Handle the exception encountered during write by canceling all the service requests that remained outstanding.
     * The exception is converted to a delphix exception before it is exposed to the initiator of the data task.
     */
    protected void handleException(Throwable t) {
        logger.errorf(t, "data sender %s failed", this);

        if (!exception.compareAndSet(null, t)) {
            t = exception.get();
        }

        // Cancel all outstanding data requests
        tracker.cancel();

        /*
         * Convert the internal throwable to an external exception before making it available via the async future
         * corresponding to the data task.
         */
        throw ExceptionUtil.getDelphixException(t);
    }

    /**
     * Finalize the data task before exiting.
     */
    protected void finish() {
        assert tracker.isDone() : "tracker still active in data task " + this;

        if (debug != null) {
            ExceptionUtil.closeIgnoreExceptions(debug);
        }

        ExceptionUtil.closeIgnoreExceptions(source);

        // If the sender has not encountered any exception so far, check if the source has and throw if so
        if (exception.get() == null) {
            source.check();
        }

        logger.debugf("data sender %s finished", this);
    }

    /**
     * Main data task loop for copying data from the source to the nexus. Exception handling is done in the caller.
     */
    protected void send() throws IOException, InterruptedException {
        ByteBuffer[] data;
        long offset = 0;

        // Start the data source.
        source.start();

        bytesToRead = getBytesToRead(nexus);

        do {
            final ServiceRequest request;

            /*
             * Try to read the desired amount of data from the source but will settle for at least a minimum amount
             * unless the end of the source has been reached. This call may block but is interruptible.
             */
            if (bytesWanted == 0) {
                data = read(bytesToRead);
            } else {
                long bytesLeft = bytesWanted - bytesSent;

                if (bytesLeft > 0) {
                    data = read((int) Math.min(bytesToRead, bytesLeft));
                } else {
                    data = null;
                }
            }

            if (data != null) {
                // The byte buffers have all been flipped, i.e., ready to be read by now
                long bytesRead = ByteBufferUtil.remaining(data);

                if (debug != null) {
                    ByteBufferUtil.writeFully(debug, ByteBufferUtil.duplicate(data));
                }

                request = createDataRequest(data, offset, false);
                offset += bytesRead;

                bytesSent += bytesRead;
            } else {
                // A null data return indicates the end of the source has been reached
                logger.debugf("eof reached on data source %s", source);

                if (eofIgnore) {
                    break;
                }

                request = createDataRequest(data, offset, true);
            }

            /*
             * Issue the data request via the service nexus and add the future to the map. This call may block if
             * the command queue is full. If interrupted, it will throw a DispatchInterruptedException.
             */
            ServiceFuture future = nexus.execute(request, new Runnable() {

                @Override
                public void run() {
                    tracker.done(request);
                }
            });

            // Track the execution of the request
            tracker.track(request, future);
        } while (data != null);

        // Wait for all outstanding data requests to complete
        tracker.awaitDone();
    }

    /**
     * Create a data request with the specified data buffers, offset, and end of data indicator, to be sent over the
     * service nexus.
     */
    protected abstract ServiceRequest createDataRequest(ByteBuffer[] data, long offset, boolean complete);

    /**
     * Return the maximum overhead of an encoded service request on top of the bulk data carried within. The overhead
     * includes the protocol framing and the non-data portion of the command.
     */
    protected abstract int getRequestOverhead();

    /**
     * Return the file to use as the debug output.
     */
    protected abstract File getDebugFile();

    /**
     * Update the progress of the data transfer.
     */
    protected abstract void updateProgress(ServiceRequest serviceRequest, ServiceResponse serviceResponse);

    /**
     * To keep data transfer at a minimum overhead, we would like to pull as much data from the source as we
     * could possibly fit into a single command. The maximum size of a command we can sent over the fore channel
     * is negotiated during nexus establishment. That, minus the overhead, gives us the maximum data size.
     * With compression enabled, the calculation may be slightly complicated. Compression may inflate the data
     * in the worst case and that must be taken into account in the estimate.
     */
    protected int getBytesToRead(ServiceNexus nexus) {
        ServiceOptions options = nexus.getOptions();

        List<String> methods = options.getOption(PAYLOAD_COMPRESS);
        CompressMethod compress = CompressMethod.valueOf(methods.get(0));
        int bytesToRead = options.getOption(FORE_MAX_REQUEST);
        int inflation = compress.estimateCompressed(bytesToRead) - bytesToRead;

        if (inflation > 0) {
            bytesToRead -= inflation;
        }

        bytesToRead -= getRequestOverhead();
        assert bytesToRead > 0;

        return bytesToRead;
    }

    @Override
    public String toString() {
        return "sender:" + source + "->" + nexus;
    }

    protected class SendResultProcessor implements AsyncResultProcessor {

        @Override
        public void process(AsyncFuture<?> asyncFuture) {
            ServiceFuture future = (ServiceFuture) asyncFuture;
            ServiceRequest request = future.getServiceRequest();
            ServiceResponse response;

            try {
                response = future.await();
            } catch (ExecutionException e) {
                // Update progress only if the request has succeeded
                return;
            } catch (CancellationException e) {
                // Update progress only if the request has succeeded
                return;
            }

            updateProgress(request, response);
        }
    }
}
