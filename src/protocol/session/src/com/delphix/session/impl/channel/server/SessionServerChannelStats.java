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

package com.delphix.session.impl.channel.server;

import java.util.HashMap;
import java.util.Map;

public class SessionServerChannelStats {

    // TODO: add histogram support

    private static final String EXECUTE_TIME = "server.sum.executeTime";
    private static final String PENDING_TIME = "server.sum.pendingTime";
    private static final String SERVICE_TIME = "server.sum.serviceTime";
    private static final String PROCESS_TIME = "server.sum.processTime";
    private static final String DISPATCH_TIME = "server.sum.dispatchTime";
    private static final String COMPLETE_TIME = "server.sum.completeTime";
    private static final String ABORT_TIME = "server.sum.abortTime";

    private static final String TOTAL_COMPLETED = "server.sum.totalCompleted";
    private static final String TOTAL_PENDING = "server.sum.totalPending";
    private static final String TOTAL_RETRIES = "server.sum.totalRetries";
    private static final String TOTAL_ABORTED = "server.sum.totalAborted";

    private static final String ORDER_DISTANCE = "server.sum.orderDistance";

    private static final String ACTIVE_COUNT = "server.now.activeCount";
    private static final String UNDONE_COUNT = "server.now.undoneCount";
    private static final String CACHED_COUNT = "server.now.cachedCount";
    private static final String MAXIMUM_CMD_SN = "server.now.maximumCmdSN";
    private static final String EXPECTED_CMD_SN = "server.now.expectedCmdSN";
    private static final String LATEST_CMD_SN = "server.now.latestCmdSN";

    private long executeTime; // Total execute time (ns)
    private long pendingTime; // Total pending time (ns)
    private long serviceTime; // Total service time (ns)
    private long processTime; // Total process time (ns)
    private long dispatchTime; // Total dispatch time (ns)
    private long completeTime; // Total complete time (ns)
    private long abortTime; // Total abort time (ns)

    private long totalCompleted; // Total number of commands processed
    private long totalPending; // Total number of commands pending
    private long totalAborted; // Total number of commands aborted
    private long totalRetries; // Total number of command retries

    private int orderDistance; // Distance from expected sequence

    private final SessionServerChannel channel; // Server channel

    public SessionServerChannelStats(SessionServerChannel channel) {
        this.channel = channel;
    }

    public synchronized void update(SessionServerCommand command) {
        SessionServerCommandStats stats = command.getStats();

        orderDistance += stats.getOrderDistance();

        if (!stats.isAborted()) {
            executeTime += stats.getExecuteTime();
            pendingTime += stats.getPendingTime();
            serviceTime += stats.getServiceTime();
            processTime += stats.getProcessTime();
            dispatchTime += stats.getDispatchTime();
            completeTime += stats.getCompleteTime();

            totalCompleted++;

            if (stats.isPending()) {
                totalPending++;
            }

            totalRetries += stats.getNumRetries();
        } else {
            abortTime += stats.getAbortTime();
            totalAborted++;
        }
    }

    public synchronized void resetStats() {
        executeTime = 0;
        pendingTime = 0;
        serviceTime = 0;
        processTime = 0;
        dispatchTime = 0;
        completeTime = 0;
        abortTime = 0;

        totalCompleted = 0;
        totalPending = 0;
        totalAborted = 0;
        totalRetries = 0;

        orderDistance = 0;
    }

    public long getExecuteTime() {
        return executeTime;
    }

    public long getPendingTime() {
        return pendingTime;
    }

    public long getServiceTime() {
        return serviceTime;
    }

    public long getProcessTime() {
        return processTime;
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

    public long getTotalAborted() {
        return totalAborted;
    }

    public long getTotalRetries() {
        return totalRetries;
    }

    public int getOrderDistance() {
        return orderDistance;
    }

    private synchronized void getSummaryStats(Map<String, Object> stats) {
        stats.put(EXECUTE_TIME, executeTime);
        stats.put(PENDING_TIME, pendingTime);
        stats.put(DISPATCH_TIME, dispatchTime);
        stats.put(SERVICE_TIME, serviceTime);
        stats.put(PROCESS_TIME, processTime);
        stats.put(COMPLETE_TIME, completeTime);
        stats.put(ABORT_TIME, abortTime);

        stats.put(TOTAL_COMPLETED, totalCompleted);
        stats.put(TOTAL_PENDING, totalPending);
        stats.put(TOTAL_RETRIES, totalRetries);
        stats.put(TOTAL_ABORTED, totalAborted);

        if (totalCompleted > 0 || totalAborted > 0) {
            float distance = (float) orderDistance / (totalCompleted + totalAborted);
            stats.put(ORDER_DISTANCE, (float) Math.round(distance * 100) / 100);
        } else {
            stats.put(ORDER_DISTANCE, 0);
        }
    }

    public Map<String, ?> getStats() {
        Map<String, Object> stats = new HashMap<String, Object>();

        getSummaryStats(stats);

        synchronized (channel) {
            stats.put(ACTIVE_COUNT, channel.getActiveCommands());
            stats.put(UNDONE_COUNT, channel.getUndoneCommands());
            stats.put(CACHED_COUNT, channel.getCachedCommands());
            stats.put(MAXIMUM_CMD_SN, channel.getMaximumCommandSN().toString());
            stats.put(EXPECTED_CMD_SN, channel.getExpectedCommandSN().toString());
            stats.put(LATEST_CMD_SN, channel.getLatestCommandSN().toString());
        }

        return stats;
    }
}
