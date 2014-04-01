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
 * Copyright (c) 2014 by Delphix. All rights reserved.
 */

package com.delphix.session.util;

import com.delphix.appliance.logger.Logger;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Decompressor;
import net.jpountz.lz4.LZ4Factory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.*;

/**
 * Compress method enumeration.
 */
public enum CompressMethod {

    COMPRESS_NONE {
        @Override
        public OutputStream createOutputStream(OutputStream os) throws IOException {
            return os;
        }

        @Override
        public InputStream createInputStream(InputStream is) throws IOException {
            return is;
        }

        @Override
        public int estimateCompressed(int size) {
            return size;
        }
    },

    COMPRESS_DEFLATE {
        @Override
        public OutputStream createOutputStream(OutputStream os) throws IOException {
            Deflater deflater = new Deflater(Deflater.BEST_SPEED);
            return new DeflaterOutputStream(os, deflater);
        }

        @Override
        public InputStream createInputStream(InputStream is) throws IOException {
            return new InflaterInputStream(is);
        }

        @Override
        public int estimateCompressed(int size) {
            return (int) Math.ceil(size * 1.00015); // RFC 1951
        }
    },

    COMPRESS_GZIP {
        @Override
        public OutputStream createOutputStream(OutputStream os) throws IOException {
            return new GZIPOutputStream(os);
        }

        @Override
        public InputStream createInputStream(InputStream is) throws IOException {
            return new GZIPInputStream(is);
        }

        @Override
        public int estimateCompressed(int size) {
            return (int) Math.ceil(size * 1.00015) + 10; // RFC 1951/1952
        }
    },

    COMPRESS_LZ4 {
        @Override
        public OutputStream createOutputStream(OutputStream os) throws IOException {
            return new LZ4OutputStream(os, lz4Codec, LZ4_BUFFER_MIN, LZ4_BUFFER_MAX);
        }

        @Override
        public InputStream createInputStream(InputStream is) throws IOException {
            return new LZ4InputStream(is, lz4Codec, LZ4_BUFFER_MAX);
        }

        /**
         * In the worst case, LZ4 with the set of parameters we have adds less than 1/32 of the original data as
         * overhead.
         */
        @Override
        public int estimateCompressed(int size) {
            return LZ4OutputStream.getMaxCompressedLength(size, LZ4_BUFFER_MIN, lz4Codec);
        }
    };

    private static final Logger logger = Logger.getLogger(CompressMethod.class);

    private static final int LZ4_BUFFER_MIN = 1024;
    private static final int LZ4_BUFFER_MAX = 65536;
    private static final LZ4Codec lz4Codec;

    static {
        /*
         * This will return the fastest LZ4 compressor that is supported on the system. There are currently three
         * implementations, namely, native or JNI based, Unsafe API based, and standard java based, ordered by the
         * speed. The JNI version requires a native library which may not be supported on the platform. If it fails
         * to load, we will fail back to the Unsafe version and then the standard java version. The standard java
         * version is only about half the speed of the native version though.
         */
        LZ4Factory factory = LZ4Factory.fastestInstance();

        logger.infof("Fastest LZ4 instance available: %s", factory);

        LZ4Compressor compressor = factory.fastCompressor();
        LZ4Decompressor decompressor = factory.decompressor();

        lz4Codec = new LZ4LengthCodec(compressor, decompressor);
    }

    /**
     * Create a compression output stream for this method.
     */
    public abstract OutputStream createOutputStream(OutputStream os) throws IOException;

    /**
     * Create a decompression input stream for this method.
     */
    public abstract InputStream createInputStream(InputStream is) throws IOException;

    /**
     * Get the compressed size for the given uncompressed size based on worst case compression ratio.
     */
    public abstract int estimateCompressed(int size);
}
