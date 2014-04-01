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

package com.delphix.session.impl.common;

import com.delphix.session.impl.channel.client.SessionClientCommand;

import java.util.concurrent.TimeUnit;

/**
 * This class implements a simple throughput throttling mechanism. The implementation is based on a token bucket algorithm
 * (see http://en.wikipedia.org/wiki/Token_bucket).
 */
public class SessionThrottler {

    // Number of tokens available in the token bucket
    private long tokens;

    // Tokens needed to issue the next command
    private long tokensNeeded;

    // Last time (in nano seconds) tokens were added to the token bucket
    private long lastUpdateNS;

    // Rate (bytes/sec) at which the token bucket is refilled
    private long fillRateBytesPerSecond;

    // Lock to synchronize access to the token bucket
    private final Object lock;

    public SessionThrottler(long fillRateBytesPerSecond, Object lock) {
        this.fillRateBytesPerSecond = fillRateBytesPerSecond;
        this.lock = lock;
        this.lastUpdateNS = System.nanoTime();
    }

    /**
     * Update the number of tokens in the token bucket.
     */
    public void updateTokens() {
        assert Thread.holdsLock(lock) : "Session throttling lock not held!";

        // Throttling disabled
        if (fillRateBytesPerSecond == 0) {
            return;
        }

        long now = System.nanoTime();
        double timeDelta = ((double) now - lastUpdateNS) / TimeUnit.SECONDS.toNanos(1);
        long tokenDelta = (long) (timeDelta * fillRateBytesPerSecond);

        // Don't lose time if not enough time has passed to add any tokens
        if (tokenDelta == 0) {
            return;
        }

        // Cap the number of tokens in the token bucket at the fill rate
        tokens = Math.min(tokens + tokenDelta, fillRateBytesPerSecond);
        lastUpdateNS = now;
    }

    /**
     * Consume the number of tokens required for the given command.
     */
    public Boolean consumeTokens(SessionClientCommand command, double compressionRatio) {
        return consumeTokens(command, compressionRatio, false);
    }

    public Boolean consumeTokens(SessionClientCommand command, double compressionRatio, Boolean waitForTokens) {
        assert Thread.holdsLock(lock) : "Session throttling lock not held!";

        // Throttling disabled
        if (fillRateBytesPerSecond == 0) {
            return true;
        }

        // Command already has tokens
        if (command.hasTokens()) {
            return true;
        }

        updateTokens();

        long commandTokens = (long) (command.getDataSize() * compressionRatio);
        if (commandTokens > tokens) {
            tokensNeeded = commandTokens;

            command.setThrottled();

            if (!waitForTokens) {
                return false;
            }

            waitForTokens();
        }

        assert tokens >= commandTokens;

        tokens -= commandTokens;
        command.setHasTokens();

        return true;
    }

    /**
     * Blocks the calling thread until the token bucket has enough tokens to issue the given command.
     */
    private void waitForTokens() {
        while (tokens < tokensNeeded) {
            long waitTokens = tokensNeeded - tokens;
            long waitMS = Math.max((long) (((double) waitTokens / fillRateBytesPerSecond) * 1000), 1);

            try {
                Thread.sleep(waitMS);
            } catch (InterruptedException e) {
                /*
                 * Even though we are ignoring the thread interrupt this wait is bounded and will not prevent the
                 * session from being shutdown. In the worst case this wait will be 1 second since the command data
                 * must be <= the fillRate. In practice these waits will often be on the order of a few milliseconds.
                 */
            }

            updateTokens();
        }

        tokensNeeded = 0;
    }

    public Boolean needTokens() {
        assert Thread.holdsLock(lock) : "Session throttling lock not held!";

        return tokensNeeded > 0;
    }
}
