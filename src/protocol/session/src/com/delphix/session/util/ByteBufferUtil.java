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

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.WritableByteChannel;

public final class ByteBufferUtil {

    public static long remaining(ByteBuffer[] bufs) {
        long byteCount = 0;

        for (int i = 0; i < bufs.length; i++) {
            byteCount += bufs[i].remaining();
        }

        return byteCount;
    }

    public static void flip(ByteBuffer[] bufs) {
        for (int i = 0; i < bufs.length; i++) {
            bufs[i].flip();
        }
    }

    public static ByteBuffer[] duplicate(ByteBuffer[] byteBuffers) {
        ByteBuffer[] newByteBuffers = new ByteBuffer[byteBuffers.length];

        for (int i = 0; i < byteBuffers.length; i++) {
            newByteBuffers[i] = byteBuffers[i].duplicate();
        }

        return newByteBuffers;
    }

    public static void corrupt(ByteBuffer[] byteBuffers) {
        for (int i = 0; i < byteBuffers.length; i++) {
            int remaining = byteBuffers[i].remaining();

            if (remaining >= 1) {
                ByteBuffer corrupt = byteBuffers[i].duplicate();
                int index = corrupt.position() + (int) (Math.random() * remaining);
                assert index >= corrupt.position() && index < (corrupt.position() + remaining);
                byte data = (byte) (~corrupt.get(index) & 0xff);
                corrupt.put(index, data);
            }
        }
    }

    public static long read(ReadableByteChannel channel, ByteBuffer[] dsts) throws IOException {
        if (channel instanceof ScatteringByteChannel) {
            ScatteringByteChannel sbc = (ScatteringByteChannel) channel;
            return sbc.read(dsts);
        }

        long totalBytesRead = -1;

        for (ByteBuffer buf : dsts) {
            // Skip to the next byte buffer with bytes remaining
            if (!buf.hasRemaining()) {
                continue;
            }

            int bytesRead = channel.read(buf);

            // Bail if EOF has been reached
            if (bytesRead < 0) {
                break;
            }

            if (totalBytesRead < 0) {
                totalBytesRead = bytesRead;
            } else {
                totalBytesRead += bytesRead;
            }

            // Bail if the current byte buffer couldn't be consumed
            if (buf.hasRemaining()) {
                break;
            }
        }

        return totalBytesRead;
    }

    public static long write(WritableByteChannel channel, ByteBuffer[] srcs) throws IOException {
        if (channel instanceof GatheringByteChannel) {
            GatheringByteChannel gbc = (GatheringByteChannel) channel;
            return gbc.write(srcs);
        }

        long count = 0;

        for (ByteBuffer buf : srcs) {
            // Skip to the next byte buffer with bytes remaining
            if (!buf.hasRemaining()) {
                continue;
            }

            count += channel.write(buf);

            // Bail if the current byte buffer couldn't be consumed
            if (buf.hasRemaining()) {
                break;
            }
        }

        return count;
    }

    public static long writeFully(WritableByteChannel channel, ByteBuffer[] srcs) throws IOException {
        long totalBytes = remaining(srcs);
        long bytesToWrite = totalBytes;
        long bytesWritten;

        do {
            bytesWritten = write(channel, srcs);
            bytesToWrite -= bytesWritten;
        } while (bytesToWrite > 0);

        return totalBytes;
    }

    public static void writeFully(FileOutputStream fos, ByteBuffer[] srcs) throws IOException {
        writeFully(fos.getChannel(), srcs);
    }

    public static void rewind(ByteBuffer[] bufs) {
        for (int i = 0; i < bufs.length; i++) {
            bufs[i].rewind();
        }
    }
}
