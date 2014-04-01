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

/**
 * This interface supports wire encoding around a given LZ4 compressor and decompressor.
 */
public interface LZ4Codec {

    /**
     * Return the maximum compressed length for an input of size length. The result should reflect the worst case
     * compression ratio supported by the compressor.
     */
    public int getMaxCompressedLength(int length);

    /**
     * Return the maximum uncompressed length for the specified input. The result may reflect the best case
     * compression ratio supported by the compressor. The codec may also keep a more precise record of the
     * uncompressed data length in the wire encoding.
     */
    public int getMaxUncompressedLength(byte[] src, int srcOff, int srcLen);

    /**
     * Compress the specified input with the given offset and length and return the compressed length.
     *
     * This method will throw LZ4Exception if the compressor is unable to compress the input into less than
     * destLen bytes. To prevent this exception to be thrown, one should make sure that destLen is always >=
     * maxCompressedLength(srcLen).
     */
    public int compress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff, int destLen);

    /**
     * Uncompress the specified input with the given offset and length and return the length of the uncompressed
     * data. Make sure destLen is the same as maxUncompressedLength(src, srcOff, srcLen) and dest large enough
     * to cover the destLen.
     */
    public int uncompress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff, int destLen);

    /**
     * Compress the specified input with the given offset and length and return the compressed data.
     */
    public byte[] compress(byte[] src, int srcOff, int srcLen);

    /**
     * Compress the specified input and return the compressed data.
     */
    public byte[] compress(byte[] src);

    /**
     * Uncompress the specified input with the given offset and length and return the uncompressed data.
     */
    public byte[] uncompress(byte[] src, int srcOff, int srcLen);

    /**
     * Uncompress the specified input and return the uncompressed data.
     */
    public byte[] uncompress(byte[] src);
}
