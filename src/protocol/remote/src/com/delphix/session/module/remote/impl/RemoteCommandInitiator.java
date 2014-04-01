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

package com.delphix.session.module.remote.impl;

import com.delphix.appliance.server.util.ExceptionUtil;
import com.delphix.session.module.remote.exception.StreamIOException;
import com.delphix.session.service.ServiceNexus;
import com.delphix.session.util.AsyncFuture;
import com.delphix.session.util.DataSource;
import com.delphix.session.util.ThreadFuture;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;
import java.util.concurrent.ExecutorService;

import static com.delphix.session.module.remote.impl.RemoteProcess.*;

/**
 * This class implements the remote command initiator. It interacts with its counterpart, LocalCommandExecutor, to
 * extend the stdin/out/err of the running process to the initiator that is located remotely from the process itself.
 *
 * The following depicts the stdin stream extension for the remotely executed process.
 *
 *                          +-----------------------------------------------------------------------+
 *                          |                                                                       |
 *       user               | sender                                  service               local   |
 *      thread              | thread                                  executor             process  |
 *                          |                                                                       |
 *      output    <======>  | stream    <------------------------>    stream    <======>    stdin   |
 *      stream      pipe    | sender               DSP                 sink       pipe       fd     |
 *                          |                                                                       |
 *                          +-----------------------------------------------------------------------+
 *                                                      remote process
 *
 * The following depicts the stdout (or stderr) stream extension for the remotely executed process.
 *
 *                          +-----------------------------------------------------------------------+
 *                          |                                                                       |
 *       user               | service                                 sender                local   |
 *      thread              | executor                                thread               process  |
 *                          |                                                                       |
 *       input    <======>  | stream    <------------------------>    stream    <======>    stdout  |
 *      stream      pipe    |  sink                DSP                sender      pipe        fd    |
 *                          |                                                                       |
 *                          +-----------------------------------------------------------------------+
 *                                                      remote process
 *
 * The semantics of the streams for a remote process are identical to those of a local process. Specifically,
 *
 *   - A user can write to stdin of a process for as long as the pipe remains open. That is true even if the process
 *     is not actively reading from it. The writer may block if the pipe runs out of buffer space. The pipe remains
 *     open unless the process has explicitly closed it or the process has terminated.
 *
 *   - When a process terminates, any attempt to write to stdin will be failed with StreamClosedException. It is the
 *     responsibility of the user to ensure the process is still around (and more generally the pipe remains open)
 *     before issuing writes to stdin.
 */
public class RemoteCommandInitiator extends AbstractStreamWriter implements StreamReader {

    private final RemoteProcess process;
    private final ServiceNexus nexus;
    private final ExecutorService executor;

    private StreamSink stdoutSink;
    private InputStream stdoutStream;

    private StreamSink stderrSink;
    private InputStream stderrStream;

    private DataSource stdinSource;
    private OutputStream stdinStream;
    private StreamSender stdinSender;
    private AsyncFuture<?> stdinTask;

    private int tag;

    public RemoteCommandInitiator(RemoteProcess process, ServiceNexus nexus, ExecutorService executor) {
        this.process = process;
        this.nexus = nexus;
        this.executor = executor;

        try {
            setupStreams();
        } catch (IOException e) {
            // Close the internal streams
            ExceptionUtil.closeIgnoreExceptions(this);

            // Destroy the external streams
            destroy();

            throw ExceptionUtil.getDelphixException(e);
        }
    }

    public OutputStream getOutputStream() {
        return stdinStream;
    }

    public InputStream getInputStream() {
        return stdoutStream;
    }

    public InputStream getErrorStream() {
        return stderrStream;
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
    public void startWrite() {
        super.startWrite();

        stdinSender = new StreamSender(this, stdinSource, nexus);
        stdinSender.setEofOnFailure(true);
        stdinSender.setType(STDIN);

        stdinTask = new ThreadFuture<Object>(stdinSender, null);
        executor.execute(stdinTask);
    }

    @Override
    public boolean read(long offset, ByteBuffer[] data, boolean sync, int type) {
        return getStreamSink(type).read(offset, data, sync);
    }

    @Override
    public boolean setEof(long offset, boolean sync, int type) {
        sync = getStreamSink(type).setEof(offset, sync);

        try {
            // Close std sink but leave the stream open to allow remaining data to be read
            getStreamSink(type).close();
        } catch (IOException e) {
            throw new StreamIOException();
        }

        return sync;
    }

    public void awaitEof(int type) {
        getStreamSink(type).awaitEof();
    }

    @Override
    public void close() throws IOException {
        /*
         * By the time this is called, the remote process has terminated or we have encountered an irrecoverable
         * communication error. Close the stdin stream, or the sink end of the pipe from which the stdin sender
         * reads. After that, any attempt to write to the stdin will fail with "stream closed" exception, which
         * is consistent with local semantics. Closing the stream also forces the stdin sender to bail in case it
         * is blocked on reading from the source end of the pipe. We should not bother sending EOF to the remote
         * in this case.
         */
        if (stdinStream != null) {
            if (stdinTask != null && !stdinTask.isDone()) {
                stdinSender.setEofIgnore(true);
            }

            ExceptionUtil.closeIgnoreExceptions(stdinStream);
        }

        /*
         * Normally, the stdin sender should be on its way out after we closed the stdin stream. However, it may
         * still be streaming lingering stdin to the remote in case the remote process terminated abnormally. Cancel
         * the stdin sender is the only way to make sure it's not stuck.
         */
        if (stdinTask != null && !stdinTask.isDone()) {
            stdinTask.cancel(true);
        }

        if (stdinSource != null) {
            stdinSource.close();
        }

        if (stdoutSink != null) {
            stdoutSink.close();
        }

        if (stderrSink != null) {
            stderrSink.close();
        }
    }

    public void destroy() {
        if (stdoutStream != null) {
            ExceptionUtil.closeIgnoreExceptions(stdoutStream);
        }

        if (stderrStream != null) {
            ExceptionUtil.closeIgnoreExceptions(stderrStream);
        }

        if (stdinStream != null) {
            ExceptionUtil.closeIgnoreExceptions(stdinStream);
        }
    }

    @Override
    public boolean isRead() {
        return false;
    }

    private void setupStreams() throws IOException {
        Pipe stdin = Pipe.open();
        String name = getStreamName(process.getArguments()[0], STDIN);
        stdinSource = new StreamSource(stdin.source(), name);
        stdinStream = Channels.newOutputStream(stdin.sink());

        Pipe stdout = Pipe.open();
        stdoutSink = new StreamSink(stdout.sink());
        stdoutStream = Channels.newInputStream(stdout.source());

        Pipe stderr = Pipe.open();
        stderrSink = new StreamSink(stderr.sink());
        stderrStream = Channels.newInputStream(stderr.source());
    }

    private StreamSink getStreamSink(int type) {
        StreamSink sink = null;

        switch (type) {
        case STDOUT:
            sink = stdoutSink;
            break;

        case STDERR:
            sink = stderrSink;
            break;

        case STDIN:
        default:
            assert false;
        }

        return sink;
    }
}
