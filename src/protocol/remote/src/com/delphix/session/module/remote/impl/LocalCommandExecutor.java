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

import com.delphix.appliance.logger.Logger;
import com.delphix.session.module.remote.exception.StreamIOException;
import com.delphix.session.service.ServiceNexus;
import com.delphix.session.util.AsyncFuture;
import com.delphix.session.util.ThreadFuture;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import static com.delphix.session.module.remote.impl.RemoteProcess.*;

/**
 * This class implements the local command executor. It is responsible for managing the locally running process on
 * behalf of a remote initiator, including the lifecycle of the running process and extending the stdin/out/err to
 * the remote counterpart.
 */
public class LocalCommandExecutor implements StreamReader, StreamWriter {

    private static final Logger logger = Logger.getLogger(LocalCommandExecutor.class);

    private final ServiceNexus nexus;
    private final ExecutorService executor;
    private final String[] arguments;

    private String[] environment;
    private String directory;
    private boolean redirectErrorStream;

    private File cwd;

    private Process process;

    private StreamSink stdinSink;

    private StreamSender stdoutSender;
    private AsyncFuture<?> stdoutTask;

    private StreamSender stderrSender;
    private AsyncFuture<?> stderrTask;

    private int tag;

    public LocalCommandExecutor(String[] arguments, ServiceNexus nexus, ExecutorService executor) {
        this.arguments = arguments;
        this.nexus = nexus;
        this.executor = executor;
    }

    public String[] getArguments() {
        return arguments;
    }

    public String[] getEnvironment() {
        return environment;
    }

    public void setEnvironment(String[] environment) {
        this.environment = environment;
    }

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;

        if (directory != null) {
            cwd = new File(directory);
        } else {
            cwd = null;
        }
    }

    public boolean isRedirectErrorStream() {
        return redirectErrorStream;
    }

    public void setRedirectErrorStream(boolean redirectErrorStream) {
        this.redirectErrorStream = redirectErrorStream;
    }

    public void waitFor() throws InterruptedException {
        logger.debugf("wait for local process %s to complete", arguments[0]);
        process.waitFor();
        logger.debugf("local process %s completed without interruption", arguments[0]);
    }

    public int getExitCode() {
        return process.exitValue();
    }

    public boolean hasExited() {
        boolean exited = true;

        try {
            getExitCode();
        } catch (IllegalThreadStateException e) {
            exited = false;
        }

        return exited;
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
        if (process == null) {
            return;
        }

        boolean exited = hasExited();

        /*
         * The local process may not have exited on its own yet if the remote execution is aborted. In this case,
         * attempt to destroy the local process to avoid getting stuck while waiting for the standard IO streams.
         * Note that process.destroy() is asynchronous with respect to the process state upkeeping. So it is more
         * prudent to wait for the process after destroy to make sure it has exited.
         */
        if (!exited) {
            process.destroy();

            while (!hasExited()) {
                try {
                    process.waitFor();
                } catch (InterruptedException e) {
                    /*
                     * We have just called process.destroy(). We want to make sure the process terminates even if
                     * we get interrupted while waiting, since we don't want to leave random processes around.
                     *
                     * And in terms of the interrupt status, this is invoked in the context of a service executor
                     * thread. We are already in the final stage of a service request execution. Once this is done,
                     * the thread will be returning to the executor and interrupt will be cleared there before it
                     * picks up something new.
                     */
                    logger.errorf(e, "interrupted while waiting for local process %s to terminate", arguments[0]);
                }
            }
        }

        logger.infof("local process %s terminated with exit code %d", arguments[0], process.exitValue());

        if (stdinSink != null) {
            stdinSink.close();
        }

        if (stdoutTask != null) {
            try {
                stdoutTask.await();
            } catch (ExecutionException e) {
                logger.errorf(e, "stdout failed");
            }
        }

        if (stderrTask != null) {
            try {
                stderrTask.await();
            } catch (ExecutionException e) {
                logger.errorf(e, "stderr failed");
            }
        }

        // In the case of normal exit, destroy the process to close the standard IO streams
        if (exited) {
            process.destroy();
        }
    }

    @Override
    public void writeReady() {

    }

    @Override
    public void startWrite() {
        try {
            ProcessBuilder builder = new ProcessBuilder(arguments);
            builder.directory(cwd);
            builder.redirectErrorStream(redirectErrorStream);

            if (environment != null) {
                Map<String, String> envmap = builder.environment();
                envmap.clear();
                for (String var : environment) {
                    String[] parts = var.split("=", 2);
                    assert parts.length == 2;
                    envmap.put(parts[0], parts[1]);
                }
            }

            logger.infof("execute local process %s env %s cwd %s", Arrays.toString(arguments),
                    Arrays.toString(environment), directory);

            process = builder.start();
        } catch (IOException e) {
            throw new StreamIOException(e);
        }

        // Set up the stdin/out/err stream remote extensions
        setupStreams(nexus, executor);
    }

    @Override
    public boolean isRead() {
        return true;
    }

    @Override
    public boolean read(long offset, ByteBuffer[] data, boolean sync, int type) {
        return stdinSink.read(offset, data, sync);
    }

    @Override
    public boolean setEof(long offset, boolean sync, int type) {
        sync = stdinSink.setEof(offset, sync);

        try {
            // Close stdin and the process may read remaining data from the other end
            stdinSink.close();

            OutputStream stdin = process.getOutputStream();
            stdin.close();
        } catch (IOException e) {
            throw new StreamIOException();
        }

        return sync;
    }

    private void setupStreams(ServiceNexus nexus, ExecutorService executor) {
        // Set up stdin
        OutputStream stdin = process.getOutputStream();
        stdinSink = new StreamSink(stdin);

        // Set up stdout
        InputStream stdout = process.getInputStream();
        String name = getStreamName(arguments[0], STDOUT);
        StreamSource stdoutSource = new StreamSource(stdout, name);

        stdoutSender = new StreamSender(this, stdoutSource, nexus);
        stdoutSender.setEofOnFailure(true);
        stdoutSender.setType(STDOUT);

        stdoutTask = new ThreadFuture<Object>(stdoutSender, null);
        executor.execute(stdoutTask);

        // Set up stderr
        InputStream stderr = process.getErrorStream();
        name = getStreamName(arguments[0], STDERR);
        StreamSource stderrSource = new StreamSource(stderr, name);

        stderrSender = new StreamSender(this, stderrSource, nexus);
        stderrSender.setEofOnFailure(true);
        stderrSender.setType(STDERR);

        stderrTask = new ThreadFuture<Object>(stderrSender, null);
        executor.execute(stderrTask);
    }
}
