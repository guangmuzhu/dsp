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
import com.delphix.appliance.server.util.ExceptionUtil;
import com.delphix.session.service.ServiceNexus;
import com.delphix.session.util.AsyncFuture;
import com.delphix.session.util.ThreadFuture;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * This class describes a process executed remotely over DSP. It extends the standard java Process class to expose
 * the exact same interface as would be used to work with a locally executed process.
 */
public class RemoteProcess extends Process {

    private static final Logger logger = Logger.getLogger(RemoteProcess.class);

    public static final int STDIN = 0;
    public static final int STDOUT = 1;
    public static final int STDERR = 2;

    private final String[] arguments;
    private final String[] environment;
    private final String directory;
    private final boolean redirectErrorStream;

    private RemoteCommandInitiator task;
    private AsyncFuture<Integer> future;

    public RemoteProcess(String[] arguments, String[] environment, String directory, boolean redirectErrorStream) {
        this.arguments = arguments;
        this.environment = environment;
        this.directory = directory;
        this.redirectErrorStream = redirectErrorStream;
    }

    public String[] getArguments() {
        return arguments;
    }

    public String[] getEnvironment() {
        return environment;
    }

    public String getDirectory() {
        return directory;
    }

    public boolean isRedirectErrorStream() {
        return redirectErrorStream;
    }

    public void execute(Callable<Integer> call, final Runnable done, ServiceNexus nexus, ExecutorService executor) {
        logger.infof("execute remote process %s env %s cwd %s", Arrays.toString(arguments),
                Arrays.toString(environment), directory);

        task = new RemoteCommandInitiator(this, nexus, executor);

        future = new ThreadFuture<Integer>(call) {

            @Override
            protected void done() {
                if (done != null) {
                    done.run();
                }
            }
        };

        executor.execute(future);
    }

    public RemoteCommandInitiator getTask() {
        return task;
    }

    @Override
    public void destroy() {
        if (!future.isDone()) {
            future.cancel(true);
        }

        logger.infof("remote process %s terminated", arguments[0]);

        task.destroy();
    }

    @Override
    public InputStream getErrorStream() {
        return task.getErrorStream();
    }

    @Override
    public InputStream getInputStream() {
        return task.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() {
        return task.getOutputStream();
    }

    @Override
    public int exitValue() {
        if (!future.isDone()) {
            throw new IllegalStateException("remote process is still running");
        }

        try {
            return waitFor();
        } catch (Throwable t) {
            throw new IllegalStateException("remote process failed");
        }
    }

    @Override
    public int waitFor() throws InterruptedException {
        try {
            logger.debugf("wait for remote process %s to complete", arguments[0]);
            int exitCode = future.get();
            logger.debugf("remote process %s completed without interruption", arguments[0]);

            return exitCode;
        } catch (ExecutionException e) {
            throw ExceptionUtil.getDelphixException(ExceptionUtil.unwrap(e));
        } catch (CancellationException e) {
            throw new InterruptedException();
        }
    }

    public static String getStreamName(String prefix, int type) {
        return prefix + "[" + type + "]";
    }
}
