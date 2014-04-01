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

package com.delphix.session.impl.client;

import com.delphix.appliance.logger.Logger;

/**
 * This class maintains connectivity stats on behalf of a client transport that include when it was created, the last
 * successful login time, the last failure time, total down time, and so on. The stats are consulted by the client
 * transport to determine when to recover a failed transport next.
 */
public class ClientTransportStats {

    private static final Logger logger = Logger.getLogger(ClientTransport.class);

    private int totalFailures; // Total number of failures in the whole lifetime
    private int numFailures; // Number of consecutive failures since last time

    private final long creationTime; // Transport creation timestamp
    private long lastLoginTime; // Last successful login timestamp
    private long lastFailureTime; // Last disconnect timestamp

    private long totalDownTime; // Time duration the transport is not logged in

    public ClientTransportStats() {
        creationTime = System.currentTimeMillis();
    }

    public ClientTransportStats(ClientTransportStats stats) {
        totalFailures = stats.totalFailures;
        numFailures = stats.numFailures;

        creationTime = stats.creationTime;
        lastLoginTime = stats.lastLoginTime;
        lastFailureTime = stats.lastFailureTime;

        totalDownTime = stats.totalDownTime;
    }

    public void transportFailed(ClientTransport xport) {
        long now = System.currentTimeMillis();

        if (numFailures > 0) {
            totalDownTime += now - lastFailureTime;
        }

        lastFailureTime = now;

        totalFailures++;
        numFailures++;

        logger.debugf("%s: failed at %tD %<tT, total failures %d, last failures %d, down time %d", xport,
                lastFailureTime, totalFailures, numFailures, totalDownTime);
    }

    public void transportLoggedIn(ClientTransport xport) {
        lastLoginTime = System.currentTimeMillis();

        logger.debugf("%s: logged in at %tD %<tT, last failure at %tD %<tT, last failures %d", xport,
                lastLoginTime, lastFailureTime, numFailures);

        if (numFailures > 0) {
            totalDownTime += lastLoginTime - lastFailureTime;
            numFailures = 0;
        }
    }

    public int getTotalFailures() {
        return totalFailures;
    }

    public int getNumFailures() {
        return numFailures;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public long getLastLoginTime() {
        return lastLoginTime;
    }

    public long getLastFailureTime() {
        return lastFailureTime;
    }

    public long getTotalDownTime() {
        return totalDownTime;
    }
}
