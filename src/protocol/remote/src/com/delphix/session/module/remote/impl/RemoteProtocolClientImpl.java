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
import com.delphix.appliance.server.exception.DelphixInterruptedException;
import com.delphix.appliance.server.util.ExceptionUtil;
import com.delphix.session.module.remote.*;
import com.delphix.session.module.remote.exception.StreamNotFoundException;
import com.delphix.session.module.remote.protocol.*;
import com.delphix.session.service.ServiceFuture;
import com.delphix.session.service.ServiceNexus;
import com.delphix.session.util.AsyncFuture;
import com.delphix.session.util.AsyncResult;
import com.delphix.session.util.ByteBufferUtil;
import com.delphix.session.util.ThreadFuture;
import com.google.common.base.Throwables;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class RemoteProtocolClientImpl implements RemoteProtocolClient {

    private static final Logger logger = Logger.getLogger(RemoteProtocolClientImpl.class);

    private final Map<Integer, StreamTask> taskMap;
    private final AtomicInteger taskTag;

    private final ExecutorService executor;

    public RemoteProtocolClientImpl(ExecutorService executor) {
        this.executor = executor;

        taskMap = new ConcurrentHashMap<Integer, StreamTask>();
        taskTag = new AtomicInteger();
    }

    public RemoteManager createRemoteManager(ServiceNexus nexus) {
        return new RemoteManagerImpl(nexus);
    }

    @Override
    public StreamDataResponse readData(ReadDataRequest request, ServiceNexus nexus) {
        int tag = request.getTask();

        StreamTask task = taskMap.get(tag);

        if (!(task instanceof StreamReader)) {
            throw new StreamNotFoundException();
        }

        StreamReader reader = (StreamReader) task;
        ByteBuffer[] data = request.getData();
        long offset = request.getOffset();
        boolean sync = false;

        if (data != null) {
            sync = reader.read(offset, data, request.isSync(), request.getType());
        }

        if (request.isEof()) {
            if (data != null) {
                ByteBufferUtil.rewind(data);
                offset += ByteBufferUtil.remaining(data);
            }

            sync = reader.setEof(offset, request.isSync(), request.getType());
        }

        StreamDataResponse response = new StreamDataResponse();

        response.setTask(tag);
        response.setSync(sync);

        return response;
    }

    @Override
    public StreamStartResponse startData(StreamStartRequest request, ServiceNexus nexus) {
        int tag = request.getTask();

        StreamTask task = taskMap.get(tag);

        if (!(task instanceof StreamWriter)) {
            throw new StreamNotFoundException();
        }

        StreamWriter writer = (StreamWriter) task;

        writer.writeReady();

        StreamStartResponse response = new StreamStartResponse();
        response.setTask(tag);

        return response;
    }

    private class RemoteManagerImpl implements RemoteManager {

        private final ServiceNexus nexus;

        public RemoteManagerImpl(ServiceNexus nexus) {
            this.nexus = nexus;
        }

        @Override
        public void readFile(String source, String target, long offset, long length, StreamProgress progress) {
            AsyncFuture<?> future = readFile(source, target, offset, length, progress, null, null);

            try {
                future.await();
            } catch (ExecutionException e) {
                throw ExceptionUtil.getDelphixException(ExceptionUtil.unwrap(e));
            } catch (CancellationException e) {
                throw new DelphixInterruptedException();
            }
        }

        @Override
        public AsyncFuture<?> readFile(final String source, final String target, final long offset, final long length,
                final StreamProgress progress, final Runnable done, AsyncResult result) {
            AsyncFuture<?> future = new ThreadFuture<Object>(new Runnable() {

                @Override
                public void run() {
                    doReadFile(source, target, offset, length, progress);
                }
            }, result) {

                @Override
                protected void done() {
                    if (done != null) {
                        done.run();
                    }
                }
            };

            executor.execute(future);

            return future;
        }

        private void doReadFile(String source, String target, long offset, long length, StreamProgress progress) {
            RemoteFileReader task = new RemoteFileReader(target, offset, length, progress);

            // Create the file reader
            create(task);

            // Set up the ReadFileRequest
            ReadFileRequest request = new ReadFileRequest();

            request.setTask(task.getTag());
            request.setPath(source);
            request.setOffset(offset);
            request.setLength(length);

            try {
                // Execute the ReadFileRequest
                ServiceFuture future = nexus.execute(request);

                // Wait for the response
                future.await();
            } catch (ExecutionException e) {
                throw ExceptionUtil.getDelphixException(ExceptionUtil.unwrap(e));
            } catch (CancellationException e) {
                throw new DelphixInterruptedException();
            } finally {
                destroy(task);
            }
        }

        @Override
        public void writeFile(String source, String target, long offset, long length, StreamProgress progress) {
            AsyncFuture<?> future = writeFile(source, target, offset, length, progress, null, null);

            try {
                future.await();
            } catch (ExecutionException e) {
                throw ExceptionUtil.getDelphixException(ExceptionUtil.unwrap(e));
            } catch (CancellationException e) {
                throw new DelphixInterruptedException();
            }
        }

        @Override
        public AsyncFuture<?> writeFile(final String source, final String target, final long offset, final long length,
                final StreamProgress progress, final Runnable done, AsyncResult result) {
            AsyncFuture<?> future = new ThreadFuture<Object>(new Runnable() {

                @Override
                public void run() {
                    doWriteFile(source, target, offset, length, progress);
                }
            }, result) {

                @Override
                protected void done() {
                    if (done != null) {
                        done.run();
                    }
                }
            };

            executor.execute(future);

            return future;
        }

        private void doWriteFile(String source, String target, long offset, long length, StreamProgress progress) {
            final RemoteFileWriter task = new RemoteFileWriter(source, offset, length, nexus, progress);

            // Create the file writer
            create(task);

            // Set up the WriteFileRequest
            WriteFileRequest request = new WriteFileRequest();

            request.setTask(task.getTag());
            request.setPath(target);
            request.setOffset(offset);
            request.setLength(length);

            try {
                // Execute the WriteFileRequest
                ServiceFuture future = nexus.execute(request, new Runnable() {

                    @Override
                    public void run() {
                        task.writeReady();
                    }
                });

                // Process the writer task
                try {
                    task.startWrite();
                } catch (Throwable t) {
                    future.cancel(true);
                    throw ExceptionUtil.getDelphixException(t);
                }

                // Wait for the response
                try {
                    future.await();
                } catch (ExecutionException e) {
                    throw ExceptionUtil.getDelphixException(ExceptionUtil.unwrap(e));
                } catch (CancellationException e) {
                    throw new DelphixInterruptedException();
                }
            } finally {
                destroy(task);
            }
        }

        @Override
        public int executeCommand(String[] arguments, String[] environment, String directory) {
            Process process = executeCommand(arguments, environment, directory, false, null);

            try {
                process.waitFor();
            } catch (InterruptedException e) {
                throw new DelphixInterruptedException();
            } finally {
                process.destroy();
            }

            return process.exitValue();
        }

        @Override
        public Process executeCommand(String[] arguments, String[] environment, String directory,
                boolean redirectErrorStream, Runnable done) {
            final RemoteProcess process = new RemoteProcess(arguments, environment, directory, redirectErrorStream);

            process.execute(new Callable<Integer>() {

                @Override
                public Integer call() {
                    return doExecuteCommand(process);
                }
            }, done, nexus, executor);

            return process;
        }

        private int doExecuteCommand(RemoteProcess process) {
            final RemoteCommandInitiator task = process.getTask();

            // Create the command executor
            create(task);

            // Create the ExecuteCommandRequest
            ExecuteCommandRequest request = new ExecuteCommandRequest();

            request.setTask(task.getTag());
            request.setArguments(process.getArguments());
            request.setEnvironment(process.getEnvironment());
            request.setDirectory(process.getDirectory());
            request.setRedirectErrorStream(process.isRedirectErrorStream());

            int exitCode;

            try {
                // Execute the ExecuteCommandRequest
                ServiceFuture future = nexus.execute(request, new Runnable() {

                    @Override
                    public void run() {
                        task.writeReady();
                    }
                });

                // Start the stdin for the remote process
                try {
                    task.startWrite();
                } catch (Throwable t) {
                    future.cancel(true);
                    throw ExceptionUtil.getDelphixException(t);
                }

                // Wait for the response
                try {
                    ExecuteCommandResponse response = (ExecuteCommandResponse) future.await();
                    exitCode = response.getExitCode();
                } catch (ExecutionException e) {
                    throw ExceptionUtil.getDelphixException(ExceptionUtil.unwrap(e));
                } catch (CancellationException e) {
                    throw new DelphixInterruptedException();
                }
            } finally {
                destroy(task);
            }

            return exitCode;
        }

        private void create(StreamTask task) {
            int tag = taskTag.getAndIncrement();

            task.setTag(tag);
            taskMap.put(tag, task);
        }

        private void destroy(StreamTask task) {
            int tag = task.getTag();

            taskMap.remove(tag);
            ExceptionUtil.closeIgnoreExceptions(task);
        }

        @Override
        public RemoteResult execute(String[] arguments, String[] environment, String directory, String stdin,
                boolean redirectErrorStream) throws InterruptedException {
            return execute(arguments, environment, directory, stdin, null, null, redirectErrorStream);
        }

        @Override
        public RemoteResult execute(String[] arguments, String[] environment, String directory, String stdin,
                StreamFilter stdoutFilter, StreamFilter stderrFilter, boolean redirectErrorStream)
                throws InterruptedException {
            Process process = executeCommand(arguments, environment, directory, redirectErrorStream, null);

            // Set up the stdout and stderr streaming
            StreamRunner stdoutRunner = new StreamRunner(process.getInputStream(), stdoutFilter);
            AsyncFuture<?> stdoutFuture = new ThreadFuture<Object>(stdoutRunner, null);
            executor.execute(stdoutFuture);

            StreamRunner stderrRunner = new StreamRunner(process.getErrorStream(), stderrFilter);
            AsyncFuture<?> stderrFuture = new ThreadFuture<Object>(stderrRunner, null);
            executor.execute(stderrFuture);

            try {
                // Set up the stdin streaming if necessary
                if (stdin != null) {
                    OutputStream os = process.getOutputStream();

                    try {
                        OutputStreamWriter writer = new OutputStreamWriter(os);

                        // Send input via stdin
                        writer.write(stdin);
                        writer.flush();
                    } catch (ClosedByInterruptException e) {
                        throw new InterruptedException();
                    } catch (IOException e) {
                        Throwables.propagate(e);
                    } finally {
                        // Close the stdin stream
                        ExceptionUtil.closeIgnoreExceptions(os);
                    }
                }

                // Wait for process to exit
                process.waitFor();

                // Quiesce the stdout and stderr streaming
                try {
                    stdoutFuture.await();
                } catch (ExecutionException e) {
                    logger.errorf(e, "stdout stream runner failed");
                }

                try {
                    stderrFuture.await();
                } catch (ExecutionException e) {
                    logger.errorf(e, "stderr stream runner failed");
                }
            } finally {
                if (!stdoutFuture.isDone()) {
                    stdoutFuture.cancel(true);
                }

                if (!stderrFuture.isDone()) {
                    stderrFuture.cancel(true);
                }

                // Destroy the process
                process.destroy();
            }

            return new RemoteResult(process.exitValue(), stdoutRunner.toString(), stderrRunner.toString());
        }

        private class StreamRunner implements Runnable {

            private final InputStream source;
            private final StreamFilter filter;
            private final StringBuilder output;

            public StreamRunner(InputStream source, StreamFilter filter) {
                if (filter == null) {
                    filter = new BufferedFilter();
                }

                this.source = source;
                this.filter = filter;

                output = new StringBuilder();
            }

            @Override
            public void run() {
                try {
                    String input;

                    while ((input = filter.read(source)) != null) {
                        output.append(input);
                    }
                } catch (IOException e) {
                    Throwables.propagate(e);
                }
            }

            @Override
            public String toString() {
                return output.toString();
            }
        }
    }

    @Override
    public Class<RemoteProtocolClient> getProtocolInterface() {
        return RemoteProtocolClient.class;
    }
}
