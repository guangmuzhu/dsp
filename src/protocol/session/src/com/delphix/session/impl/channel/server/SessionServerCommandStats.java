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

package com.delphix.session.impl.channel.server;

public class SessionServerCommandStats {

    private long startTime; // Command start time
    private long restartTime; // Command restart time
    private long invokeTime; // Command invoke time
    private long processTime; // Command process time
    private long finishTime; // Command finish time
    private long completeTime; // Command complete time (initial)
    private long retryTime; // Command retry time (most recent)
    private long abortTime; // Command abort time (initial)

    private int numRetries; // Command retries
    private int numAborts; // Command aborts

    private int orderDistance; // Distance from expected sequence

    public SessionServerCommandStats() {

    }

    public void start() {
        restartTime = System.nanoTime();

        if (startTime == 0) {
            startTime = restartTime;
        }
    }

    public void complete() {
        retryTime = System.nanoTime();

        if (completeTime == 0) {
            completeTime = retryTime;
        }
    }

    public void invoke() {
        invokeTime = System.nanoTime();
    }

    public void process() {
        processTime = System.nanoTime();
    }

    public void finish() {
        finishTime = System.nanoTime();
    }

    public void abort() {
        numAborts++;

        if (abortTime == 0) {
            abortTime = System.nanoTime();
        }
    }

    public boolean isPending() {
        return restartTime > startTime;
    }

    public boolean isAborted() {
        return abortTime > 0;
    }

    public void retry() {
        numRetries++;
    }

    public void setOrderDistance(int orderDistance) {
        this.orderDistance = orderDistance;
    }

    public int getOrderDistance() {
        return orderDistance;
    }

    /**
     * Return the time it took to execute the command from start to the initial response.
     */
    public long getExecuteTime() {
        return completeTime - startTime;
    }

    /**
     * Return the time the command was pending start waiting for sequence ordering among other things.
     */
    public long getPendingTime() {
        return restartTime - startTime;
    }

    /**
     * Return the time it took to dispatch the command from (re)start til the service is invoked. It will be zero if
     * the command has never made to the service invocation.
     */
    public long getDispatchTime() {
        return invokeTime > 0 ? invokeTime - restartTime : 0;
    }

    /**
     * Return the time it took to execute the service from invocation to completion. It will be zero if the command
     * has never made to the service invocation.
     */
    public long getServiceTime() {
        return finishTime > 0 ? finishTime - invokeTime : 0;
    }

    /**
     * Return the time it took to execute the service from process to completion. It will be zero if the command
     * has never made to the service execution.
     */
    public long getProcessTime() {
        return finishTime > 0 ? finishTime - processTime : 0;
    }

    /**
     * Return the time it took to complete the command from service finish til response is sent. If will be be zero
     * if the command has never made to the service invocation.
     */
    public long getCompleteTime() {
        return finishTime > 0 ? completeTime - finishTime : 0;
    }

    /**
     * Return the time it took to execute the command from start to the most recent response send.
     */
    public long getTotalExecuteTime() {
        return retryTime - startTime;
    }

    /**
     * Return the time it took to abort the command from when the abort is requested til the service finishes. It
     * will be zero if the command has never made to the service invocation.
     */
    public long getAbortTime() {
        if (!isAborted()) {
            return 0;
        }

        return finishTime > abortTime ? finishTime - abortTime : 0;
    }

    /**
     * Return the number of times the command was retried due to transport.
     */
    public int getNumRetries() {
        return numRetries;
    }

    /**
     * Return the number of times the command was aborted.
     */
    public int getNumAborts() {
        return numAborts;
    }
}
