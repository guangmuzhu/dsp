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

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Decompressor;
import net.jpountz.lz4.LZ4Exception;

/**
 * The LZ4 codec writes the uncompressed length before the compressed data in order to speed up decompression. It
 * is possible to not encode the uncompressed data length with the currently LZ4 native interface. But we would be
 * forced to use a slower call that doesn't assume the size if known.
 */
public class LZ4LengthCodec extends LZ4AbstractCodec {

    private final LZ4Compressor compressor;
    private final LZ4Decompressor decompressor;

    public LZ4LengthCodec(LZ4Compressor compressor, LZ4Decompressor decompressor) {
        this.compressor = compressor;
        this.decompressor = decompressor;
    }

    @Override
    public int getMaxCompressedLength(int length) {
        // The extra four bytes is required to encode the uncompressed data length
        return compressor.maxCompressedLength(length) + 4;
    }

    @Override
    public int getMaxUncompressedLength(byte[] src, int srcOff, int srcLen) {
        // The input buffer must have at least the uncompressed data length
        if (srcLen <= 4) {
            throw new LZ4Exception("Malformed stream");
        }

        // Return the precise uncompressed data length encoded in the stream
        return ((src[srcOff++] & 0xFF) << 24)
                | ((src[srcOff++] & 0xFF) << 16)
                | ((src[srcOff++] & 0xFF) << 8)
                | (src[srcOff] & 0xFF);
    }

    @Override
    public int compress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff, int destLen) {
        // Write the uncompressed data length before the compressed data
        dest[destOff++] = (byte) (srcLen >>> 24);
        dest[destOff++] = (byte) (srcLen >>> 16);
        dest[destOff++] = (byte) (srcLen >>> 8);
        dest[destOff++] = (byte) srcLen;

        // Return the compressed length including the uncompressed data length
        return 4 + compressor.compress(src, srcOff, srcLen, dest, destOff, destLen - 4);
    }

    @Override
    public int uncompress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff, int destLen) {
        // The decompressor interface assumes destLen is precisely the uncompressed data length
        int compressedLen = 4 + decompressor.decompress(src, srcOff + 4, dest, destOff, destLen);

        if (compressedLen != srcLen) {
            throw new LZ4Exception("Uncompressed length mismatch " + srcLen + " != " + compressedLen);
        }

        return destLen;
    }
}
