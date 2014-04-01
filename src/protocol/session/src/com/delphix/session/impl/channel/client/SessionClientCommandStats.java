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

package com.delphix.session.impl.channel.client;

public class SessionClientCommandStats {

    private long startTime; // Command start time
    private long restartTime; // Command restart time
    private long sendTime; // Command send time (initial)
    private long resendTime; // Command resend time (most recent)
    private long receiveTime; // Command receive time (most recent)
    private long completeTime; // Command complete time
    private long abortTime; // Command abort time

    private long dataSize; // Number of data bytes
    private long compressedDataSize; // Number of compressed data bytes

    private int numResets; // Command resets
    private boolean throttled; // Command was bandwidth throttled

    public SessionClientCommandStats() {

    }

    public void start() {
        restartTime = System.nanoTime();

        if (startTime == 0) {
            startTime = restartTime;
        }
    }

    public void send() {
        resendTime = System.nanoTime();

        if (sendTime == 0) {
            sendTime = resendTime;
        }
    }

    public void receive() {
        receiveTime = System.nanoTime();
    }

    public void complete() {
        completeTime = System.nanoTime();
    }

    public void abort() {
        abortTime = System.nanoTime();
    }

    public void reset() {
        receiveTime = System.nanoTime();
        numResets++;
    }

    public void setThrottled() {
        throttled = true;
    }

    public void setDataSize(long dataSize) {
        this.dataSize = dataSize;
    }

    public void setCompressedDataSize(long compressedDataSize) {
        this.compressedDataSize = compressedDataSize;
    }

    public boolean isPending() {
        return restartTime > startTime;
    }

    public boolean isAborted() {
        return abortTime > 0;
    }

    /**
     * Return the time it took to execute the command from start to completion.
     */
    public long getExecuteTime() {
        return completeTime - startTime;
    }

    /**
     * Return the time the command was pending start waiting for slot availability among other things.
     */
    public long getPendingTime() {
        return restartTime - startTime;
    }

    /**
     * Return the total network round-trip time from when the command was first sent til it was last returned from
     * a possibly different transport. It will be zero if the command has never made to the transport before it is
     * aborted.
     */
    public long getNetworkTime() {
        return receiveTime > 0 ? receiveTime - sendTime : 0;
    }

    /**
     * Return the last network round-trip time from when the command was last sent til it was returned from the
     * transport. It will be zero if the command has never made to the transport before it is aborted.
     */
    public long getLastNetworkTime() {
        return receiveTime > 0 ? receiveTime - resendTime : 0;
    }

    /**
     * Return the time it took to dispatch a command from start to the first time it was sent over transport. It
     * will be zero if the command has never made to the transport before it is aborted.
     */
    public long getDispatchTime() {
        return sendTime > 0 ? sendTime - restartTime : 0;
    }

    /**
     * Return the time it took to complete a command from when it came back from transport til the command callback
     * is invoked. It will be zero if the command has never made to the transport before it is aborted.
     */
    public long getCompleteTime() {
        return receiveTime > 0 ? completeTime - receiveTime : 0;
    }

    /**
     * Return the time it took to abort a command from when the abort was requested til the command was completed.
     */
    public long getAbortTime() {
        if (!isAborted()) {
            return 0;
        }

        return completeTime > abortTime ? completeTime - abortTime : 0;
    }

    /**
     * Return the number of times the command was reset due to transport.
     */
    public int getNumResets() {
        return numResets;
    }

    /**
     * Return true if the command dispatch was delayed due to bandwidth throttling.
     */
    public boolean isThrottled() {
        return throttled;
    }

    /**
     * Return the data size for the command.
     */
    public long getDataSize() {
        return dataSize;
    }

    /**
     * Return the compressed data size for the command.
     */
    public long getCompressedDataSize() {
        return compressedDataSize;
    }
}
