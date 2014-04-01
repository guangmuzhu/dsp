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

import java.util.HashMap;
import java.util.Map;

public class SessionClientChannelStats {

    // TODO: add histogram support

    private static final String EXECUTE_TIME = "client.sum.executeTime";
    private static final String PENDING_TIME = "client.sum.pendingTime";
    private static final String NETWORK_TIME = "client.sum.networkTime";
    private static final String DISPATCH_TIME = "client.sum.dispatchTime";
    private static final String COMPLETE_TIME = "client.sum.completeTime";
    private static final String ABORT_TIME = "client.sum.abortTime";

    private static final String TOTAL_BYTES = "client.sum.totalBytes";
    private static final String TOTAL_COMPRESSED_BYTES = "client.sum.totalCompressedBytes";

    private static final String TOTAL_COMPLETED = "client.sum.totalCompleted";
    private static final String TOTAL_PENDING = "client.sum.totalPending";
    private static final String TOTAL_RESETS = "client.sum.totalReset";
    private static final String TOTAL_ABORTS = "client.sum.totalAborted";
    private static final String TOTAL_THROTTLED = "client.sum.totalThrottled";

    private static final String ACTIVE_COUNT = "client.now.activeCount";
    private static final String PENDING_COUNT = "client.now.pendingCount";
    private static final String RETRY_COUNT = "client.now.retryCount";
    private static final String ABORT_COUNT = "client.now.abortCount";
    private static final String TOTAL_COUNT = "client.now.totalCount";
    private static final String CURRENT_CMD_SN = "client.now.currentCmdSN";
    private static final String EXPECTED_CMD_SN = "client.now.expectedCmdSN";

    private long executeTime; // Total execute time (ns)
    private long pendingTime; // Total pending time (ns)
    private long networkTime; // Total network time (ns)
    private long dispatchTime; // Total dispatch time (ns)
    private long completeTime; // Total complete time (ns)
    private long abortTime; // Total abort time (ns)

    private long totalCompleted; // Total number of commands completed
    private long totalPending; // Total number of commands pending
    private long totalReset; // Total number of commands reset

    private long totalBytes; // Total number of data bytes
    private long totalCompressedBytes; // Total number of compressed data bytes

    private long totalAborted; // Total number of commands aborted
    private long totalThrottled; // Total number of commands bandwidth throttled

    private final SessionClientChannel channel; // Client channel

    public SessionClientChannelStats(SessionClientChannel channel) {
        this.channel = channel;
    }

    public synchronized void update(SessionClientCommand command) {
        SessionClientCommandStats stats = command.getStats();

        if (!stats.isAborted()) {
            executeTime += stats.getExecuteTime();
            pendingTime += stats.getPendingTime();
            networkTime += stats.getNetworkTime();
            dispatchTime += stats.getDispatchTime();
            completeTime += stats.getCompleteTime();

            totalBytes += stats.getDataSize();
            totalCompressedBytes += stats.getCompressedDataSize();

            totalCompleted++;

            if (stats.isThrottled()) {
                totalThrottled++;
            }

            if (stats.isPending()) {
                totalPending++;
            }

            totalReset += stats.getNumResets();
        } else {
            abortTime += stats.getAbortTime();
            totalAborted++;
        }
    }

    public synchronized void resetStats() {
        executeTime = 0;
        pendingTime = 0;
        networkTime = 0;
        dispatchTime = 0;
        completeTime = 0;
        abortTime = 0;

        totalCompleted = 0;
        totalPending = 0;
        totalReset = 0;

        totalBytes = 0;
        totalCompressedBytes = 0;

        totalAborted = 0;
        totalThrottled = 0;
    }

    public long getExecuteTime() {
        return executeTime;
    }

    public long getPendingTime() {
        return pendingTime;
    }

    public long getNetworkTime() {
        return networkTime;
    }

    public long getDispatchTime() {
        return dispatchTime;
    }

    public long getCompleteTime() {
        return completeTime;
    }

    public long getAbortTime() {
        return abortTime;
    }

    public long getTotalCompleted() {
        return totalCompleted;
    }

    public long getTotalPending() {
        return totalPending;
    }

    public long getTotalReset() {
        return totalReset;
    }

    public long getTotalAborted() {
        return totalAborted;
    }

    public long getTotalThrottled() {
        return totalThrottled;
    }

    public long getTotalDataBytes() {
        return totalBytes;
    }

    public long getTotalCompressedDataBytes() {
        return totalCompressedBytes;
    }

    public double getCompressionRatio() {
        if (totalBytes == 0) {
            return 1.0;
        }

        return (double) totalCompressedBytes / totalBytes;
    }

    private synchronized void getSummaryStats(Map<String, Object> stats) {
        stats.put(EXECUTE_TIME, getExecuteTime());
        stats.put(PENDING_TIME, getPendingTime());
        stats.put(DISPATCH_TIME, getDispatchTime());
        stats.put(NETWORK_TIME, getNetworkTime());
        stats.put(COMPLETE_TIME, getCompleteTime());
        stats.put(ABORT_TIME, getAbortTime());

        stats.put(TOTAL_BYTES, getTotalDataBytes());
        stats.put(TOTAL_COMPRESSED_BYTES, getTotalCompressedDataBytes());

        stats.put(TOTAL_COMPLETED, totalCompleted);
        stats.put(TOTAL_PENDING, totalPending);
        stats.put(TOTAL_RESETS, totalReset);
        stats.put(TOTAL_ABORTS, totalAborted);
        stats.put(TOTAL_THROTTLED, totalThrottled);
    }

    public Map<String, ?> getStats() {
        Map<String, Object> stats = new HashMap<String, Object>();

        getSummaryStats(stats);

        synchronized (channel) {
            stats.put(ACTIVE_COUNT, channel.getActiveCommands());
            stats.put(RETRY_COUNT, channel.getRetryCommands());
            stats.put(ABORT_COUNT, channel.getAbortCommands());
            stats.put(TOTAL_COUNT, channel.getTotalCommands());
            stats.put(PENDING_COUNT, channel.getPendingCommands());
            stats.put(CURRENT_CMD_SN, channel.getCommandSN().toString());
            stats.put(EXPECTED_CMD_SN, channel.getExpectedCommandSN().toString());
        }

        return stats;
    }
}
