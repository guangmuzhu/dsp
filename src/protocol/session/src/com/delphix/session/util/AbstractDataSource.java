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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

/**
 * This class provides an abstract data source implementation based on a readable byte channel. It supports scatter
 * gather style read interface using ByteBuffer[] and data gathering to avoid read spuriously returning a low byte
 * count. If the readable byte channel is an instance of ScatteringByteChannel, we will use scattering capability;
 * otherwise, we will emulate that going over the ByteBuffer[]. The data source will be interruptible if the byte
 * channel is an instance of InterruptibleChannel, such as FileChannel, SocketChannel, Pipe.*Channel.
 */
public abstract class AbstractDataSource implements DataSource {

    protected static final Logger logger = Logger.getLogger(AbstractDataSource.class);

    // Default settings for minimum bytes read and buffer allocation granularity
    private static final int MIN_BYTES_READ = 512;
    private static final int MAX_BUFFER_SIZE = 65536;

    protected ReadableByteChannel channel;

    // The maximum size of a single byte buffer
    protected int maxBufferSize;

    // The minimum bytes to be returned for each read
    protected int minBytesRead;

    protected AbstractDataSource() {
        this(null);
    }

    protected AbstractDataSource(ReadableByteChannel channel) {
        this(channel, MAX_BUFFER_SIZE, MIN_BYTES_READ);
    }

    protected AbstractDataSource(ReadableByteChannel channel, int maxBufferSize, int minReadBytes) {
        this.channel = channel;

        this.maxBufferSize = maxBufferSize;
        this.minBytesRead = minReadBytes;
    }

    @Override
    public int getMaxBufferSize() {
        return maxBufferSize;
    }

    @Override
    public void setMaxBufferSize(int maxBufferSize) {
        this.maxBufferSize = maxBufferSize;
    }

    @Override
    public int getMinBytesRead() {
        return minBytesRead;
    }

    @Override
    public void setMinBytesRead(int minBytesRead) {
        this.minBytesRead = minBytesRead;
    }

    /**
     * Try to fill up the remaining space left in the specified byte buffer array with data read from the source
     * channel. It will return -1 if there is no more data left in the source channel; otherwise, it will return
     * the number of bytes read. It will read at least the minimum bytes read before returning unless EOF has been
     * reached.
     */
    @Override
    public long read(ByteBuffer[] dsts) throws IOException {
        long bytesWanted = ByteBufferUtil.remaining(dsts);
        long totalBytesRead = -1;

        do {
            long bytesRead = ByteBufferUtil.read(channel, dsts);

            // EOF reached
            if (bytesRead < 0) {
                break;
            }

            if (bytesRead < bytesWanted && bytesRead < minBytesRead) {
                logger.tracef("short read over channel %s - bytes wanted %d and got %d",
                        channel, bytesWanted, bytesRead);
            }

            bytesWanted -= bytesRead;

            if (totalBytesRead < 0) {
                totalBytesRead = bytesRead;
            } else {
                totalBytesRead += bytesRead;
            }
        } while (totalBytesRead < minBytesRead && bytesWanted > 0);

        // Flip the byte buffers for read
        ByteBufferUtil.flip(dsts);

        return totalBytesRead;
    }

    /**
     * Similar to above except reading into a segment of the byte buffer array starting at the given offset with the
     * specified length.
     */
    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        if (offset < 0 || length < 0 || offset > dsts.length - length) {
            throw new IndexOutOfBoundsException();
        }

        ByteBuffer[] bufs = new ByteBuffer[length];
        System.arraycopy(dsts, offset, bufs, 0, length);

        return read(bufs);
    }

    /**
     * Similar to above except using ByteBuffer.
     */
    @Override
    public int read(ByteBuffer dst) throws IOException {
        return (int) read(new ByteBuffer[] { dst });
    }

    /**
     * Try to read the expected size from the source channel. It will return null if there is no more data left
     * in the source channel; otherwise, it will return a byte buffer array that contains the data read. It will
     * read at least the minimum bytes read before returning unless EOF has been reached.
     */
    @Override
    public ByteBuffer[] read(int expected) throws IOException {
        ByteBuffer[] dsts = new ByteBuffer[(expected + maxBufferSize - 1) / maxBufferSize];

        for (int i = 0; i < dsts.length; i++) {
            int capacity;

            if (expected > maxBufferSize) {
                capacity = maxBufferSize;
            } else {
                capacity = expected;
            }

            expected -= capacity;

            dsts[i] = ByteBuffer.allocate(capacity);
        }

        return read(dsts) < 0 ? null : dsts;
    }

    /**
     * Return true if the data source is open and false otherwise.
     */
    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    /**
     * Close the data source by closing the file channel.
     */
    @Override
    public void close() throws IOException {
        channel.close();
    }

    /**
     * Check if the data source has encountered an exception. Override this method only if the data source permits
     * exceptions generated from alternative avenues than simply reading from file channel.
     */
    @Override
    public void check() {

    }

    /**
     * Start the data source.
     */
    @Override
    public void start() {

    }
}
