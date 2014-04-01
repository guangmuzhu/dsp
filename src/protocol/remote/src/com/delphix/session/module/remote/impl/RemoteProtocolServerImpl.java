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
import com.delphix.session.module.remote.RemoteProtocolServer;
import com.delphix.session.module.remote.exception.StreamIOException;
import com.delphix.session.module.remote.exception.StreamInterruptedException;
import com.delphix.session.module.remote.exception.StreamNotFoundException;
import com.delphix.session.module.remote.exception.StreamNotStartedException;
import com.delphix.session.module.remote.protocol.*;
import com.delphix.session.service.ServiceFuture;
import com.delphix.session.service.ServiceNexus;
import com.delphix.session.util.ByteBufferUtil;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class RemoteProtocolServerImpl implements RemoteProtocolServer {

    private final Map<Integer, StreamTask> taskMap;
    private final ExecutorService executor;

    public RemoteProtocolServerImpl(ExecutorService executor) {
        this.executor = executor;

        taskMap = new ConcurrentHashMap<Integer, StreamTask>();
    }

    @Override
    public StreamDataResponse writeData(WriteDataRequest request, ServiceNexus nexus) {
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

        // Return the data response
        StreamDataResponse response = new StreamDataResponse();

        response.setTask(tag);
        response.setSync(sync);

        return response;
    }

    @Override
    public ReadFileResponse readFile(ReadFileRequest request, ServiceNexus nexus) {
        int tag = request.getTask();

        RemoteFileWriter task;

        try {
            // Create the file writer
            task = new RemoteFileWriter(request.getPath(), request.getOffset(), request.getLength(), nexus);
        } catch (Throwable t) {
            throw new StreamIOException(t);
        }

        // Set the writer ready to go
        task.writeReady();
        task.setRead(true);

        // Create the file writer
        create(tag, task);

        try {
            // Process the writer task
            task.startWrite();
        } catch (Throwable t) {
            throw new StreamIOException(t);
        } finally {
            destroy(task);
        }

        // Return the read response
        ReadFileResponse response = new ReadFileResponse();

        response.setTask(tag);
        response.setLength(task.getBytesSent());

        return response;
    }

    @Override
    public WriteFileResponse writeFile(WriteFileRequest request, ServiceNexus nexus) {
        int tag = request.getTask();

        RemoteFileReader task;

        try {
            task = new RemoteFileReader(request.getPath(), request.getOffset(), request.getLength());
        } catch (Throwable t) {
            throw new StreamIOException(t);
        }

        // Create the file reader
        create(tag, task);

        try {
            StreamStartRequest start = new StreamStartRequest();

            start.setTask(tag);

            // Notify the writer to start
            ServiceFuture future = nexus.execute(start);

            try {
                future.await();
            } catch (ExecutionException e) {
                throw new StreamNotStartedException(e);
            } catch (CancellationException e) {
                throw new StreamInterruptedException();
            }

            // Wait for reader to complete
            try {
                task.awaitEof();
            } catch (Throwable t) {
                throw new StreamInterruptedException(t);
            }
        } finally {
            destroy(task);
        }

        // Return the write response
        WriteFileResponse response = new WriteFileResponse();

        response.setTask(tag);

        return response;
    }

    @Override
    public ExecuteCommandResponse executeCommand(ExecuteCommandRequest request, ServiceNexus nexus) {
        int tag = request.getTask();

        LocalCommandExecutor task = new LocalCommandExecutor(request.getArguments(), nexus, executor);

        task.setEnvironment(request.getEnvironment());
        task.setDirectory(request.getDirectory());
        task.setRedirectErrorStream(request.isRedirectErrorStream());

        // Create the command executor
        create(tag, task);

        try {
            try {
                task.startWrite();
            } catch (Throwable t) {
                throw new StreamIOException(t);
            }

            StreamStartRequest start = new StreamStartRequest();

            start.setTask(tag);

            // Notify the stdin to start
            ServiceFuture future = nexus.execute(start);

            try {
                future.await();
            } catch (ExecutionException e) {
                throw new StreamNotStartedException(e);
            } catch (CancellationException e) {
                throw new StreamInterruptedException();
            }

            // Wait for command to complete
            try {
                task.waitFor();
            } catch (Throwable t) {
                throw new StreamInterruptedException(t);
            }
        } finally {
            destroy(task);
        }

        // Return the execute response
        ExecuteCommandResponse response = new ExecuteCommandResponse();

        response.setTask(tag);
        response.setCode(task.getExitCode());

        return response;
    }

    private void create(int tag, StreamTask task) {
        taskMap.put(tag, task);
        task.setTag(tag);
    }

    private void destroy(StreamTask task) {
        int tag = task.getTag();

        taskMap.remove(tag);
        ExceptionUtil.closeIgnoreExceptions(task);
    }

    @Override
    public Class<RemoteProtocolServer> getProtocolInterface() {
        return RemoteProtocolServer.class;
    }
}
