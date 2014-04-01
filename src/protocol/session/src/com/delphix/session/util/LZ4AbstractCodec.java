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

import java.util.Arrays;

public abstract class LZ4AbstractCodec implements LZ4Codec {

    @Override
    public byte[] compress(byte[] src, int srcOff, int srcLen) {
        int maxLength = getMaxCompressedLength(srcLen);
        byte[] compressed = new byte[maxLength];

        int compressedLen = compress(src, srcOff, srcLen, compressed, 0, maxLength);

        if (compressedLen == maxLength) {
            return compressed;
        } else {
            return Arrays.copyOf(compressed, compressedLen);
        }
    }

    @Override
    public byte[] compress(byte[] src) {
        return compress(src, 0, src.length);
    }

    @Override
    public byte[] uncompress(byte[] src, int srcOff, int srcLen) {
        int maxLength = getMaxUncompressedLength(src, srcOff, srcLen);
        byte[] uncompressed = new byte[maxLength];

        int uncompressedLen = uncompress(src, srcOff, srcLen, uncompressed, 0, maxLength);

        if (uncompressedLen == maxLength) {
            return uncompressed;
        } else {
            return Arrays.copyOf(uncompressed, uncompressedLen);
        }
    }

    @Override
    public byte[] uncompress(byte[] src) {
        return uncompress(src, 0, src.length);
    }
}
