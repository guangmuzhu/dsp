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

package com.delphix.session.module.remote;

import com.delphix.session.util.AsyncFuture;
import com.delphix.session.util.AsyncResult;

/**
 * Remote manager interface. The manager interface captures all the APIs that this service module offers to a higher
 * level consumer of the protocol service.
 *
 * All references to path names in a file system, whether for file transfer APIs or command execution APIs, must
 * conform to the requirements for the platform which the references point to. For example, to read a file from
 * a Windows host to a UN*X host, the source must be specified in a UNC format that is understood by the Windows
 * host, such as C:\temp\myfile, and the target must follow the UN*X path name convention. For details, please
 * refer to the JDK doc for File.
 */
public interface RemoteManager {

    /**
     * Read the content of the remote source file and write to the local target starting from the given offset for
     * the specified length. The interface is synchronous and the caller will block until transfer is complete.
     *
     * The optional stream progress may be specified for interaction with the data transfer.
     */
    public void readFile(String source, String target, long offset, long length, StreamProgress progress);

    /**
     * Read the content of the local source file and write to the remote target starting from the given offset for
     * the specified length. The interface is synchronous and the caller will block until transfer is complete.
     *
     * The optional stream progress may be specified for interaction with the data transfer.
     */
    public void writeFile(String source, String target, long offset, long length, StreamProgress progress);

    /**
     * Execute the command remotely. This interface is synchronous and the caller will block until process exits.
     */
    public int executeCommand(String[] arguments, String[] environment, String directory);

    /**
     * Read the content of the remote source file and write to the local target starting from the given offset for
     * the specified length. The interface is asynchronous with execution controlled by the returned future.
     *
     * The optional stream progress may be specified for interaction with the data transfer. The done callback, if
     * any, will be invoked when the data transfer is complete. The optional async result may be used together with
     * an async tracker.
     */
    public AsyncFuture<?> readFile(String source, String target, long offset, long length,
            StreamProgress progress, Runnable done, AsyncResult result);

    /**
     * Read the content of the local source file and write to the remote target starting from the given offset for
     * the specified length. The interface is asynchronous with execution controlled by the returned future.
     *
     * The optional stream progress may be specified for interaction with the data transfer. The done callback, if
     * any, will be invoked when the data transfer is complete. The optional async result may be used together with
     * an async tracker.
     */
    public AsyncFuture<?> writeFile(String source, String target, long offset, long length,
            StreamProgress progress, Runnable done, AsyncResult result);

    /**
     * Execute the command remotely. The interface is asynchronous with execution and standard streams controlled
     * by the returned process.
     */
    public Process executeCommand(String[] arguments, String[] environment, String directory,
            boolean redirectErrorStream, Runnable done);

    /**
     * Execute the command remotely. The interface is synchronous with the resulting status and standard streams
     * returned in RemoteResult.
     */
    public RemoteResult execute(String[] arguments, String[] environment, String directory, String stdin,
            boolean redirectErrorStream) throws InterruptedException;

    /**
     * Same as above with the added custom output filtering capabilities.
     */
    public RemoteResult execute(String[] arguments, String[] environment, String directory, String stdin,
            StreamFilter stdoutFilter, StreamFilter stderrFilter, boolean redirectErrorStream)
            throws InterruptedException;
}
