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

import com.delphix.appliance.server.util.ExceptionUtil;
import com.delphix.session.module.remote.StreamProgress;
import com.delphix.session.module.remote.exception.StreamIOException;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

/**
 * This class implements the file reader task that reads data from a remote source and writes it into a local file.
 * It allows reading from a specified offset and for a desired length to support resumable read.
 */
public class RemoteFileReader implements StreamReader {

    private final StreamProgress progress;

    private final String path;
    private final long offset;
    private final long length;

    private final RandomAccessFile file;
    private final StreamSink sink;

    private int tag;

    public RemoteFileReader(String path, long offset, long length) {
        this(path, offset, length, null);
    }

    public RemoteFileReader(String path, long offset, long length, StreamProgress progress) {
        this.progress = progress;

        this.path = path;
        this.offset = offset;
        this.length = length;

        try {
            file = new RandomAccessFile(path, "rw");

            if (offset > 0) {
                file.seek(offset);
            }
        } catch (IOException e) {
            throw ExceptionUtil.getDelphixException(e);
        }

        sink = new StreamSink(file.getChannel(), offset, progress);
    }

    public String getPath() {
        return path;
    }

    public long getLength() {
        return length;
    }

    public long getOffset() {
        return offset;
    }

    @Override
    public boolean read(long offset, ByteBuffer[] data, boolean sync, int type) {
        // Override the sync indicator
        if (progress != null) {
            sync = progress.sync(offset, false);
        }

        return sink.read(this.offset + offset, data, sync);
    }

    @Override
    public boolean setEof(long offset, boolean sync, int type) {
        // Override the sync indicator
        if (progress != null) {
            sync = progress.sync(offset, true);
        }

        sync = sink.setEof(offset, sync);

        try {
            close();
        } catch (IOException e) {
            throw new StreamIOException();
        }

        return sync;
    }

    public void awaitEof() {
        sink.awaitEof();
    }

    @Override
    public int getTag() {
        return tag;
    }

    @Override
    public void setTag(int tag) {
        this.tag = tag;
    }

    @Override
    public void close() throws IOException {
        sink.close();
        file.close();
    }
}
