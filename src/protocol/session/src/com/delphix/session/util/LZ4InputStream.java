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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Uncompress a stream which has been compressed with LZ4OutputStream. The same LZ4Codec must be provided as the one
 * which has been used for compression. For detailed description of the wire encoding, refer to LZ4OutputStream.
 *
 * If more data is requested than the next chunk is uncompressed to, the result is placed into the user specified
 * buffer directly without additional copy; otherwise, a staging buffer is used so that additional data may be held
 * temporarily until it is read.
 *
 * In general, we don't know the chunk sizes of the compressed stream, whether it is compressed and uncompressed. To
 * accommodate, the internal buffers used here are dynamically expandable.
 */
public class LZ4InputStream extends InputStream {

    private static final int DEFAULT_BUFFER_SIZE = 4096;

    private final InputStream is;
    private final LZ4Codec codec;

    // Staging buffer for unread uncompressed data
    private byte[] stagingBuffer;
    private int stagingOffset;
    private int stagingLength;

    // Encoded buffer for the next compressed data chunk
    private byte[] encodedBuffer;
    private int encodedLength;

    private final byte[] oneByteBuffer;

    public LZ4InputStream(InputStream is, LZ4Codec codec, int bufferSize) {
        this.is = is;
        this.codec = codec;

        this.stagingBuffer = new byte[bufferSize];
        this.encodedBuffer = new byte[bufferSize];
        this.oneByteBuffer = new byte[1];
    }

    public LZ4InputStream(InputStream is, LZ4Codec codec) {
        this(is, codec, DEFAULT_BUFFER_SIZE);
    }

    private void ensureOpen() throws IOException {
        if (stagingOffset == -1) {
            throw new IOException("Already closed");
        }
    }

    @Override
    public void close() throws IOException {
        ensureOpen();

        stagingOffset = -1;

        super.close();
        is.close();
    }

    @Override
    public int available() throws IOException {
        ensureOpen();
        return stagingLength;
    }

    @Override
    public int read() throws IOException {
        return read(oneByteBuffer) == -1 ? -1 : oneByteBuffer[0] & 0xFF;
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        ensureOpen();

        // Return now if no bytes desired
        if (len == 0) {
            return 0;
        }

        int count = 0;

        // Pick up from where we left in the staging buffer
        if (stagingLength > 0) {
            int copied = Math.min(len, stagingLength);

            System.arraycopy(stagingBuffer, stagingOffset, buf, off, copied);

            stagingOffset += copied;
            stagingLength -= copied;

            off += copied;
            len -= copied;
            count += copied;

            if (len == 0) {
                return count;
            }
        }

        while (true) {
            assert stagingLength == 0;

            /*
             * Read the compressed data length. If EOF is reached in the input stream and we have no data to return,
             * simply return -1 to signal EOF. Otherwise, we will return the number of bytes read.
             */
            if (!readAtLeast(4)) {
                return count == 0 ? -1 : count;
            }

            int compressedLength = ((encodedBuffer[0] & 0xFF) << 24)
                    | ((encodedBuffer[1] & 0xFF) << 16)
                    | ((encodedBuffer[2] & 0xFF) << 8)
                    | (encodedBuffer[3] & 0xFF);

            if (encodedBuffer.length < 4 + compressedLength) {
                encodedBuffer = expandBuffer(encodedBuffer, compressedLength + 4);
            }

            // Read the compressed data with uncompressed length at the beginning
            boolean success = readAtLeast(4 + compressedLength);
            assert success : "malformed input data exception expected";

            // Get the uncompressed data length for the compressed chunk
            int maxLength = codec.getMaxUncompressedLength(encodedBuffer, 4, compressedLength);

            if (maxLength > len) {
                /*
                 * Uncompress into a staging buffer if there is not enough space left for the whole chunk and we
                 * have not returned any data at all in this invocation yet.
                 */
                if (count == 0) {
                    if (stagingBuffer.length < maxLength) {
                        stagingBuffer = expandBuffer(stagingBuffer, maxLength);
                    }

                    stagingOffset = 0;
                    stagingLength = codec.uncompress(encodedBuffer, 4, compressedLength, stagingBuffer, 0, maxLength);

                    rewindEncodedBuffer(4 + compressedLength);

                    int copied = Math.min(len, stagingLength);

                    System.arraycopy(stagingBuffer, stagingOffset, buf, off, copied);

                    stagingOffset += copied;
                    stagingLength -= copied;

                    off += copied;
                    len -= copied;
                    count += copied;
                }

                break;
            }

            // Uncompress directly into the user buffer if there is enough space left for the whole chunk
            int uncompressedLength = codec.uncompress(encodedBuffer, 4, compressedLength, buf, off, maxLength);

            rewindEncodedBuffer(4 + compressedLength);

            off += uncompressedLength;
            len -= uncompressedLength;
            count += uncompressedLength;
        }

        return count;
    }

    /**
     * Read at least the given length of bytes from the input stream. The assumption is that this is the start of
     * a new compressed data chunk. The method may block until it's done. If EOF is reached before at least length
     * bytes are read _and_ we have read part of the new chunk, an exception is thrown.
     */
    private boolean readAtLeast(int len) throws IOException {
        while (encodedLength < len) {
            int inc = is.read(encodedBuffer, encodedLength, encodedBuffer.length - encodedLength);

            if (inc == -1) {
                if (encodedLength == 0) {
                    return false;
                } else {
                    throw new EOFException("Malformed input data");
                }
            }

            encodedLength += inc;
        }

        return true;
    }

    /**
     * Move encoded data to the beginning of the encoded buffer. This is necessary because we may read at least the
     * specified length which implies possibly more than the compressed data chunk at a time.
     */
    private void rewindEncodedBuffer(int len) {
        encodedLength -= len;

        if (encodedLength > 0) {
            System.arraycopy(encodedBuffer, len, encodedBuffer, 0, encodedLength);
        }
    }

    /**
     * Expand the buffer to at least the given length and possibly doubling of the origin length.
     */
    private byte[] expandBuffer(byte[] buf, int len) {
        return Arrays.copyOf(buf, Math.max(len, buf.length * 2));
    }
}
