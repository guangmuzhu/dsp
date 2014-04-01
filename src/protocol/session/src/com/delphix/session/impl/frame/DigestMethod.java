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

package com.delphix.session.impl.frame;

import com.delphix.session.impl.common.BadDigestException;

import java.nio.ByteBuffer;
import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

/**
 * Digest method enumeration.
 */
public enum DigestMethod {

    DIGEST_NONE {
        @Override
        public int size() {
            return 0;
        }

        @Override
        public Checksum create() {
            return null;
        }

        @Override
        public byte[] toByteArray(Checksum digest) {
            return null;
        }

        @Override
        public long fromByteArray(byte[] array) {
            return 0;
        }
    },

    DIGEST_CRC32 {
        @Override
        public int size() {
            return Integer.SIZE / Byte.SIZE;
        }

        @Override
        public Checksum create() {
            return new CRC32();
        }

        @Override
        public byte[] toByteArray(Checksum digest) {
            return DigestMethod.toByteArray(size(), digest.getValue());
        }

        @Override
        public long fromByteArray(byte[] array) {
            return DigestMethod.fromByteArray(size(), array);
        }
    },

    DIGEST_ADLER32 {
        @Override
        public int size() {
            return Integer.SIZE / Byte.SIZE;
        }

        @Override
        public Checksum create() {
            return new Adler32();
        }

        @Override
        public byte[] toByteArray(Checksum digest) {
            return DigestMethod.toByteArray(size(), digest.getValue());
        }

        @Override
        public long fromByteArray(byte[] array) {
            return DigestMethod.fromByteArray(size(), array);
        }
    };

    /**
     * Return the size of the checksum in bytes.
     */
    public abstract int size();

    /**
     * Create a checksum for this digest method.
     */
    public abstract Checksum create();

    /**
     * Convert the digest value to byte array in network byte order.
     */
    public abstract byte[] toByteArray(Checksum digest);

    /**
     * Convert the byte array from network byte order to digest value.
     */
    public abstract long fromByteArray(byte[] array);

    private static byte[] toByteArray(int size, long value) {
        byte[] array = new byte[size];

        for (int i = size; i > 0; i--) {
            array[i - 1] = (byte) value;
            value >>= Byte.SIZE;
        }

        return array;
    }

    private static long fromByteArray(int size, byte[] array) {
        long value = 0L;

        if (size != array.length) {
            throw new BadDigestException("digest size mismatch");
        }

        for (int i = 0; i < size; i++) {
            value <<= Byte.SIZE;
            value += (long) array[i] & 0xff;
        }

        return value;
    }

    /**
     * Update the checksum with the byte buffer array.
     */
    public static void updateDataDigest(ByteBuffer[] buffers, Checksum digest) {
        for (ByteBuffer buffer : buffers) {
            /*
             * ChannelBuffer.toByteBuffers() uses ByteBuffer.wrap(byte[] array, int offset, int length) internally.
             * One thing odd about ByteBuffer.wrap() is that it doesn't set the array offset field. Instead, it only
             * sets the current position of the buffer. So even if ByteBuffer.hasArray() is true, one could be fooled
             * using that since ByteBuffer.arrayOffset() returns 0. A workaround to this JDK oddity is to create a
             * slice first, which sets the offset to the ByteBuffer's current position.
             */
            buffer = buffer.slice();

            // Data copy may be avoided if the byte buffer is backed by byte[]
            if (buffer.hasArray()) {
                digest.update(buffer.array(), buffer.arrayOffset(), buffer.remaining());
            } else {
                byte[] array = new byte[buffer.remaining()];
                buffer.get(array);
                digest.update(array, 0, array.length);
            }
        }
    }
}
