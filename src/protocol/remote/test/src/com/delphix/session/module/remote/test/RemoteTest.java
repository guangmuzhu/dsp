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

package com.delphix.session.module.remote.test;

import com.delphix.session.module.remote.BufferedFilter;
import com.delphix.session.module.remote.RemoteResult;
import com.delphix.session.module.remote.StreamFilter;
import com.delphix.session.module.remote.StreamProgress;
import com.delphix.session.util.AsyncFuture;
import org.testng.annotations.Test;

import java.io.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.*;

public class RemoteTest extends RemoteBaseTest {

    private static final long MB = 1024L * 1024L;

    private AsyncFuture<?> asyncIO(long length, boolean read, StreamProgress progress) {
        AsyncFuture<?> future;

        if (read) {
            future = remoteManager.readFile("/dev/zero", "/dev/null", 0, length, progress, null, null);
        } else {
            future = remoteManager.writeFile("/dev/zero", "/dev/null", 0, length, progress, null, null);
        }

        return future;
    }

    private long syncIO(long length, boolean read, StreamProgress progress) {
        long start = System.nanoTime();

        AsyncFuture<?> future = asyncIO(length, read, progress);

        try {
            future.await();
        } catch (ExecutionException e) {
            fail("file IO failure", e.getCause());
        } catch (CancellationException e) {
            fail("file IO cancelled");
        }

        return System.nanoTime() - start;
    }

    @Test
    public void readLoop() {
        long elapsed = 0;

        for (int i = 0; i < 512; i++) {
            elapsed += syncIO(1 * MB, true, null);
        }

        System.out.format("read loop: %s files/s\n", TimeUnit.SECONDS.toNanos(1) * 512 / elapsed);
    }

    @Test
    public void readFile() {
        long elapsed;

        for (int i = 0; i < 3; i++) {
            elapsed = syncIO(256 * MB, true, null);
            System.out.format("read warmup: %s MB\n", TimeUnit.SECONDS.toNanos(1) * 256 / elapsed);
        }

        elapsed = syncIO(2048 * MB, true, null);
        System.out.format("read test: %s MB/s\n", TimeUnit.SECONDS.toNanos(1) * 2048 / elapsed);
    }

    @Test
    public void writeFile() {
        long elapsed;

        for (int i = 0; i < 3; i++) {
            elapsed = syncIO(256 * MB, false, null);
            System.out.format("write warmup: %s MB\n", TimeUnit.SECONDS.toNanos(1) * 256 / elapsed);
        }

        elapsed = syncIO(2048 * MB, false, null);
        System.out.format("write test: %s MB/s\n", TimeUnit.SECONDS.toNanos(1) * 2048 / elapsed);
    }

    @Test
    public void cancelRead() {
        AsyncFuture<?> future = asyncIO(1024 * MB, true, null);

        try {
            Thread.sleep(250);
        } catch (InterruptedException e) {
            fail("test interrupted");
        }

        future.cancel(true);
    }

    @Test
    public void cancelWrite() {
        AsyncFuture<?> future = asyncIO(1024 * MB, false, null);

        try {
            Thread.sleep(250);
        } catch (InterruptedException e) {
            fail("test interrupted");
        }

        future.cancel(true);
    }

    @Test
    public void remoteExecute() throws Exception {
        String[] args = new String[] { "cat" };
        String stdin = "Knock knock!";

        RemoteResult result = remoteManager.execute(args, null, null, stdin, false);

        assertTrue(result.hasStdout());
        assertEquals(result.getStdout(), stdin);
    }

    @Test
    public void outputFilter() throws Exception {
        final AtomicInteger counter = new AtomicInteger();

        // Create a simple line based filter to count the number of comments
        StreamFilter filter = new BufferedFilter() {

            @Override
            protected String filter() throws IOException {
                String line = reader.readLine();

                if (line == null) {
                    return null;
                }

                if (line.startsWith("#")) {
                    counter.getAndIncrement();
                }

                return line + "\n";
            }
        };

        String[] args = new String[] { "cat" };
        String stdin = "# filter test\nKnock knock\n# the end\n";

        RemoteResult result = remoteManager.execute(args, null, null, stdin, filter, null, false);

        assertTrue(result.hasStdout());
        assertEquals(result.getStdout(), stdin);
        assertEquals(counter.get(), 2);
    }

    @Test
    public void redirectErrorStream() throws Exception {
        String[] args = new String[] { "sh", "-c", "echo a; echo b >&2; echo c; echo d >&2" };

        RemoteResult result = remoteManager.execute(args, null, null, null, true);

        assertEquals(result.getStdout(), "a\nb\nc\nd\n");
        assertEquals(result.getStderr(), "");
    }

    @Test
    public void executeCommand() throws Exception {
        // Execute local command
        String[] localArgs = { "ls", "-l" };
        Process local = Runtime.getRuntime().exec(localArgs);
        InputStream localStdout = local.getInputStream();

        // Execute remote command
        String[] remoteArgs = { "wc", "-l" };
        Process remote = remoteManager.executeCommand(remoteArgs, null, null, false, null);
        OutputStream remoteStdin = remote.getOutputStream();

        // Read from local stdout and write to remote stdin
        LineNumberReader localReader = new LineNumberReader(new InputStreamReader(localStdout, "UTF-8"));
        String line;

        while ((line = localReader.readLine()) != null) {
            remoteStdin.write(line.getBytes());
            remoteStdin.write("\n".getBytes());
        }

        remoteStdin.flush();

        // Wait for local command to complete
        int exitCode = local.waitFor();
        assertEquals(exitCode, 0);

        local.destroy();

        // Close the remote stdin to force remote to go
        remoteStdin.close();

        // Wait for remote command to complete
        exitCode = remote.waitFor();
        assertEquals(exitCode, 0);

        // Parse remote output - a single line with line count
        InputStream remoteStdout = remote.getInputStream();

        LineNumberReader remoteReader = new LineNumberReader(new InputStreamReader(remoteStdout, "UTF-8"));
        int lineNumber = 0;

        while ((line = remoteReader.readLine()) != null) {
            try {
                lineNumber = Integer.valueOf(line.trim());
            } catch (NumberFormatException e) {
                fail("invalid output " + line);
            }
        }

        // Make sure the output is sane
        assertEquals(remoteReader.getLineNumber(), 1);
        assertEquals(localReader.getLineNumber(), lineNumber);

        remote.destroy();
    }

    @Test
    public void cancelExecute() throws Exception {
        // Execute local command
        String[] localArgs = { "ls", "-l" };
        Process local = Runtime.getRuntime().exec(localArgs);
        InputStream localStdout = local.getInputStream();

        // Execute remote command
        String[] remoteArgs = { "wc", "-l" };
        Process remote = remoteManager.executeCommand(remoteArgs, null, null, false, null);
        OutputStream remoteStdin = remote.getOutputStream();

        // Read from local stdout and write to remote stdin
        byte[] buffer = new byte[256];
        int count;

        while ((count = localStdout.read(buffer)) != -1) {
            remoteStdin.write(buffer, 0, count);
        }

        local.waitFor();
        local.destroy();

        // Leave the remote stdin open to keep the remote process running
        Thread.sleep(250);

        // Cancel the remote process
        remote.destroy();
    }

    @Test
    public void cancelTest() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        Thread thread = new Thread() {

            @Override
            public void run() {
                // Release the main thread
                latch.countDown();

                try {
                    String[] remoteArgs = { "/bin/sh", "-c", "sleep 3600" };
                    remoteManager.execute(remoteArgs, null, null, null, false);
                } catch (InterruptedException e) {
                    logger.info("remote execution interrupted as expected");
                }
            }
        };

        thread.start();

        // Wait for the thread to start
        latch.await();

        // Let the thread run for a short while
        Thread.sleep(1000);

        thread.interrupt();
        thread.join();
    }

    @Test
    public void readProgress() {
        TestProgress progress = new TestProgress(0, 256 * MB);
        syncIO(256 * MB, true, progress);
        progress.validate();
    }

    @Test
    public void writeProgress() {
        TestProgress progress = new TestProgress(0, 256 * MB);
        syncIO(256 * MB, false, progress);
        progress.validate();
    }

    private class TestProgress implements StreamProgress {

        private final long startOffset;
        private final long totalLength;

        private boolean syncPending;
        private long syncOffset;
        private long byteCount;

        public TestProgress(long startOffset, long totalLength) {
            this.startOffset = startOffset;
            this.totalLength = totalLength;
            this.syncOffset = startOffset;
        }

        @Override
        public void update(long offset, long length, boolean eof, boolean sync) {
            byteCount += length;

            // Progress update may be out of order
            assertTrue(offset >= startOffset);
            assertTrue(byteCount <= totalLength);

            if (eof) {
                assertEquals(offset + length, startOffset + totalLength);
            }

            if (sync) {
                assertTrue(syncPending);
                syncPending = false;

                if (syncOffset < offset + length) {
                    syncOffset = offset + length;
                }
            }
        }

        @Override
        public boolean sync(long offset, boolean eof) {
            boolean sync = false;

            if (!syncPending) {
                sync = offset - syncOffset >= 16 * MB || eof;

                if (sync) {
                    syncPending = true;
                }
            }

            return sync;
        }

        public void validate() {
            assertEquals(byteCount, totalLength);
        }
    }
}
