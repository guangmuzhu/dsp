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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public abstract class AbstractStreamProgress {

    protected static final Logger logger = Logger.getLogger(AbstractStreamProgress.class);

    // The total amount of data expected
    protected long totalLength;

    // The total number of streams expected
    protected int totalStreams;

    // Update interval in percent complete
    protected int updateInterval;

    // The percentage of completion
    protected int percentComplete;

    // Individual stream statistics
    protected Map<String, StreamStat> active;
    protected Map<String, StreamStat> done;

    // Aggregate statistics
    protected StreamStat aggr;

    protected AbstractStreamProgress() {
        // Individual stream statistics
        active = new HashMap<String, StreamStat>();
        done = new HashMap<String, StreamStat>();

        // Aggregate statistics
        aggr = new StreamStat();
    }

    public long getTotalLength() {
        return totalLength;
    }

    public void setTotalLength(long totalLength) {
        this.totalLength = totalLength;
    }

    public int getTotalStreams() {
        return totalStreams;
    }

    public void setTotalStreams(int totalStreams) {
        this.totalStreams = totalStreams;
    }

    public long getUpdateInterval() {
        return updateInterval;
    }

    public void setUpdateInterval(int updateInterval) {
        this.updateInterval = updateInterval;
    }

    public int getPercentComplete() {
        return percentComplete;
    }

    public void setPercentComplete(int percentComplete) {
        this.percentComplete = percentComplete;
    }

    public long getBytesXferred() {
        return aggr.getBytesXferred();
    }

    public void start(String stream) {
        start(stream, 0);
    }

    public synchronized void start(String stream, long length) {
        if (!aggr.started()) {
            aggr.start(totalLength);
        }

        StreamStat stat = new StreamStat(stream);
        stat.start(length);

        StreamStat prev = active.put(stream, stat);
        assert prev == null;
    }

    public synchronized void stop(String stream) {
        StreamStat stat = active.remove(stream);
        assert stat != null;

        done.put(stream, stat);

        // Record the completion time for the stream
        stat.stop();

        // Update upon stream completion
        update();
    }

    public synchronized void stop() {
        aggr.stop();
    }

    public synchronized void transfer(String stream, long bytesXferred) {
        StreamStat stat = active.get(stream);
        assert stat != null;

        // Record the bytes transferred
        stat.transfer(bytesXferred);
        aggr.transfer(bytesXferred);

        // Update upon data transfer
        update();
    }

    public synchronized void jumpStart(long bytesXferred) {
        // Record the bytes previously transferred
        aggr.jumpStart(bytesXferred);

        // Update upon data transfer
        update();
    }

    private int percent(long numerator, long denominator) {
        return (int) (Math.min((double) numerator / denominator, 1.0) * 100);
    }

    private void update() {
        int newPercent = 0;

        // Get the new percent complete based on bytes transferred or streams completed
        if (totalLength > 0) {
            newPercent = percent(aggr.getBytesXferred(), totalLength);
        } else if (totalStreams > 0) {
            newPercent = percent(done.size(), totalStreams);
        }

        /*
         * Update percent complete if the new percent complete has advanced beyond the highest seen so far. The new
         * percent complete may come in lower in case the stream transfer is restarted during a failed attempt.
         */
        if (percentComplete < newPercent) {
            int oldPercent = percentComplete;

            percentComplete = newPercent;

            // Run periodic update if percent complete has advanced beyond the update interval
            if (oldPercent + updateInterval <= percentComplete) {
                // Update active streams
                for (StreamStat value : active.values()) {
                    assert value.active();
                    value.update();
                }

                // Update aggregate stat
                aggr.update();

                // Update progress callback
                updateProgress();
            }
        }
    }

    /**
     * Convert an elapsed time into HH:MM:SS format.
     */
    public static String formatTime(long seconds) {
        long hours = TimeUnit.SECONDS.toHours(seconds);
        seconds -= TimeUnit.SECONDS.convert(hours, TimeUnit.HOURS);

        long minutes = TimeUnit.SECONDS.toMinutes(seconds);
        seconds -= TimeUnit.SECONDS.convert(minutes, TimeUnit.MINUTES);

        return String.format("%d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * Convert a number to a human-readable string with suffixes.
     */
    public static String formatNumber(long number) {
        String[] suffixes = new String[] { "", "K", "M", "G", "T", "P", "E" };

        int suffix = 0;
        double raw = number;
        while (raw > 1024 && suffix < suffixes.length - 1) {
            raw /= 1024;
            suffix++;
        }

        if (raw < 10) {
            return String.format("%.3g%sB", raw, suffixes[suffix]);
        } else {
            return String.format("%.4g%sB", raw, suffixes[suffix]);
        }
    }

    /**
     * Stream progress update callback.
     */
    protected abstract void updateProgress();

    // Stream statistics
    protected class StreamStat {

        private final String name;

        private long length;

        private long startTime;
        private long stopTime;
        private long bytesXferred;
        private long lastXferred;

        private long updateTime;
        private long bytesXferredAtLastUpdate;

        private long bytesXferredSinceLastUpdate;
        private long timeElapsedSinceLastUpdate;

        public StreamStat() {
            this("<aggregate>");
        }

        public StreamStat(String name) {
            this.name = name;
        }

        public boolean started() {
            return startTime > 0;
        }

        public boolean active() {
            return startTime > 0 && stopTime == 0;
        }

        public void start(long length) {
            this.length = length;

            startTime = System.nanoTime();
            updateTime = startTime;
        }

        public void stop() {
            stopTime = System.nanoTime();
        }

        public void update() {
            long currentTime = System.nanoTime();

            bytesXferredSinceLastUpdate = bytesXferred - bytesXferredAtLastUpdate;
            timeElapsedSinceLastUpdate = currentTime - updateTime;

            bytesXferredAtLastUpdate = bytesXferred;
            updateTime = currentTime;
        }

        public void transfer(long bytesXferred) {
            this.bytesXferred += bytesXferred;
        }

        public void jumpStart(long lastXferred) {
            this.lastXferred = lastXferred;
        }

        public long getBytesXferred() {
            return lastXferred + bytesXferred;
        }

        public long getElapsedTime() {
            if (startTime > 0) {
                if (stopTime > 0) {
                    return stopTime - startTime;
                } else {
                    return System.nanoTime() - startTime;
                }
            } else {
                return 0;
            }
        }

        public long getRecentThroughput() {
            if (timeElapsedSinceLastUpdate > 0) {
                return (long) (bytesXferredSinceLastUpdate / ((double) timeElapsedSinceLastUpdate / TimeUnit.SECONDS.toNanos(1)));
            } else {
                return 0;
            }
        }

        public long getAverageThroughput() {
            long elapsedTime = getElapsedTime();

            if (elapsedTime > 0) {
                return (long) (bytesXferred / ((double) elapsedTime / TimeUnit.SECONDS.toNanos(1)));
            } else {
                return 0;
            }
        }

        @Override
        public String toString() {
            return String.format("stream %s transferred %s length %s elapsed %s average %s/s recent %s/s",
                    name, formatNumber(bytesXferred), formatNumber(length),
                    formatTime(TimeUnit.NANOSECONDS.toSeconds(getElapsedTime())),
                    formatNumber(getAverageThroughput()), formatNumber(getRecentThroughput()));
        }
    }
}
