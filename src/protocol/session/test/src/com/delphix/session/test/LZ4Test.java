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

package com.delphix.session.test;

import com.delphix.appliance.server.test.UnitTest;
import com.delphix.session.util.LZ4Codec;
import com.delphix.session.util.LZ4InputStream;
import com.delphix.session.util.LZ4LengthCodec;
import com.delphix.session.util.LZ4OutputStream;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Decompressor;
import net.jpountz.lz4.LZ4Factory;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@UnitTest
public class LZ4Test {

    public void runTest(LZ4Codec codec, int len, int maxBuf, int maxValue) throws IOException {
        byte[] buf = new byte[len];
        Random r = new Random(0);
        for (int i = 0; i < len; ++i) {
            buf[i] = (byte) r.nextInt(maxValue);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        LZ4OutputStream os = new LZ4OutputStream(out, codec, maxBuf);

        int off = 0;

        while (off < len) {
            int l = r.nextInt(1 - off + len);
            os.write(buf, off, l);
            off += l;
        }

        os.close();

        // test full read
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        LZ4InputStream is = new LZ4InputStream(in, codec);

        byte[] restored = new byte[len + 100];
        off = 0;

        while (off < len) {
            int read = is.read(restored, off, restored.length - off);
            assertTrue(read >= 0);
            off += read;
        }

        is.close();

        assertEquals(len, off);
        assertEquals(buf, Arrays.copyOf(restored, len));

        // test partial reads
        in = new ByteArrayInputStream(out.toByteArray());
        is = new LZ4InputStream(in, codec);

        restored = new byte[len + 100];
        off = 0;

        while (off < len) {
            int toRead = Math.min(r.nextInt(64), restored.length - off);
            int read = is.read(restored, off, toRead);
            assertTrue(read >= 0);
            off += read;
        }

        is.close();

        assertEquals(len, off);
        assertEquals(buf, Arrays.copyOf(restored, len));
    }

    @Test
    public void testLZ4() throws IOException {
        LZ4Factory factory = LZ4Factory.fastestInstance();

        LZ4Compressor compressor = factory.fastCompressor();
        LZ4Decompressor decompressor = factory.decompressor();

        LZ4Codec codec = new LZ4LengthCodec(compressor, decompressor);

        int[] lens = new int[] { 0, 1, 10, 1024, 512 * 1024 };
        int[] maxBufs = new int[] { 1, 100, 2048, 32 * 1024 };
        int[] maxValues = new int[] { 5, 10, 50, 256 };

        for (int len : lens) {
            for (int maxBuf : maxBufs) {
                for (int maxValue : maxValues) {
                    runTest(codec, len, maxBuf, maxValue);
                }
            }
        }
    }
}
