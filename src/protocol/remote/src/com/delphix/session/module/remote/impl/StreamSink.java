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

package com.delphix.session.module.remote.impl;

import com.delphix.appliance.server.exception.DelphixInterruptedException;
import com.delphix.session.module.remote.StreamProgress;
import com.delphix.session.module.remote.exception.StreamIOException;
import com.delphix.session.util.ByteBufferUtil;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

/**
 * This class implements a data sink that supports sequential streaming write access. It maintains the current offset
 * into the stream to ensure that writes are indeed sequential and uses the WritableByteChannel for IO operations.
 */
public class StreamSink implements Closeable {

    private final WritableByteChannel channel;
    private final OutputStream stream;
    private final StreamProgress progress;

    private long current;
    private boolean eof;

    public StreamSink(OutputStream stream) {
        this(stream, 0, null);
    }

    public StreamSink(OutputStream stream, long offset, StreamProgress progress) {
        this(stream, Channels.newChannel(stream), offset, progress);
    }

    public StreamSink(WritableByteChannel channel) {
        this(channel, 0, null);
    }

    public StreamSink(WritableByteChannel channel, long offset, StreamProgress progress) {
        this(null, channel, offset, progress);
    }

    private StreamSink(OutputStream stream, WritableByteChannel channel, long offset, StreamProgress progress) {
        this.stream = stream;
        this.channel = channel;
        this.current = offset;
        this.progress = progress;
    }

    public boolean read(long offset, ByteBuffer[] data, boolean sync) {
        long bytesWritten;

        /*
         * Read should be issued and processed sequentially under normal circumstances. In case of exception or
         * cancellation, however, it may leave a hole in the file offset issued by the read requests. When a
         * gap has been detected, the sink should be closed immediately.
         */
        if (current != offset) {
            throw new StreamIOException("offset mismatch - expected " + current + " actual " + offset);
        }

        try {
            bytesWritten = ByteBufferUtil.writeFully(channel, data);
        } catch (IOException e) {
            throw new StreamIOException(e);
        }

        current += bytesWritten;

        return update(offset, bytesWritten, false, sync);
    }

    public boolean setEof(long offset, boolean sync) {
        assert current == offset;

        sync = update(current, 0, true, sync);

        synchronized (this) {
            if (!eof) {
                eof = true;
                notify();
            }
        }

        return sync;
    }

    public synchronized void awaitEof() {
        while (!eof) {
            try {
                wait();
            } catch (InterruptedException e) {
                throw new DelphixInterruptedException();
            }
        }
    }

    @Override
    public void close() throws IOException {
        channel.close();

        if (stream != null) {
            stream.close();
        }
    }

    private boolean update(long offset, long length, boolean eof, boolean sync) {
        if (sync) {
            if (channel instanceof FileChannel) {
                FileChannel file = (FileChannel) channel;

                try {
                    file.force(true);
                } catch (IOException e) {
                    throw new StreamIOException(e);
                }
            } else {
                sync = false;
            }
        }

        if (progress != null) {
            progress.update(offset, length, eof, sync);
        }

        return sync;
    }
}
