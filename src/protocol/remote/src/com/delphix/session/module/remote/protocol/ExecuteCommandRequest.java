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

package com.delphix.session.module.remote.protocol;

import com.delphix.session.module.remote.RemoteProtocolServer;
import com.delphix.session.service.ServiceNexus;
import com.delphix.session.service.ServiceResponse;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * The ExecuteCommandRequest is sent to request execution of an executable resident on the remote host. An active
 * instance of command execution may have three data streams associated with it, namely, stdin, stdout, and stderr.
 * These streams may need to remain active during the execution of a command with unbounded lengths. The command
 * execution and its associated streams are strung together with the opaque stream identification tag. Multiple
 * instances command execution may be supported as long as the tag is unique. Each of the 3 streams is assigned a
 * different type for further differentiation.
 *
 * The following diagram illustrates the protocol interactions. Note that it is allowed for stdin, stdout, and
 * stderr streams to be interleaved as long as each stream is received in order even though the diagram showed
 * otherwise.
 *
 *      ExecuteCommandRequest              ---->
 *
 *                                         <----                        StreamStartRequest
 *      StreamStartResponse                ---->
 *
 *      StreamStdinRequest {EOF=false}     ---->
 *                                         <----                        StreamStdinResponse
 *                                          ...
 *      StreamStdinRequest {EOF=true}      ---->
 *                                         <----                        StreamStdinResponse
 *
 *                                         <----                        StreamStdoutRequest {EOF=false}
 *      StreamStdoutResponse               ---->
 *                                          ...
 *                                         <----                        StreamStdoutRequest {EOF=true}
 *      StreamStdoutResponse               ---->
 *
 *                                         <----                        StreamStderrRequest {EOF=false}
 *      StreamStderrResponse               ---->
 *                                          ...
 *                                         <----                        StreamStderrRequest {EOF=true}
 *      StreamStderrResponse               ---->
 *
 *                                         <----                        ExecuteCommandResponse
 *
 * Failure scenarios for command execution are described as follows.
 *
 *      - If an error is detected on the command executioner while processing ExecuteCommandRequest, an exception
 *        is sent instead of a ExecuteCommandResponse to conclude the command execution.
 *
 *      - If an error is detected on the command initiator while writing the data from stdout or stderr, an
 *        exception is sent instead of StreamDataResponse. The command executioner should close the corresponding
 *        stream. It is up to the running command process to deal with the stdout or stderr closing.
 *
 *      - If an error is detected on the command executioner while reading from stdout or stderr, the corresponding
 *        io streams will be closed and StreamDataRequest sent with EOF set to true.
 *
 *      - If an error is detected on the command executioner while writing to stdin, the corresponding io stream is
 *        immediately closed and future update to stdin from the command initiator is discarded. An exception is sent
 *        instead of StreamDataResponse.
 *
 *      - If an error is detected on the command initiator while gathering data for stdin, a StreamDataRequest is
 *        sent with EOF set to true which leads to the immediate closing of stdin on the command executioner. It
 *        is up to the running command process to deal with the stdin closing.
 *
 *      - In case we want to cancel the command execution from the initiator, the outstanding CommandExecutionRequest
 *        is cancelled which results in an internal task management event delivered to the executioner.
 *
 * The ExecuteCommandRequest includes the following fields.
 *
 *      arguments               The array containing the command to call and its arguments. The pathname of the
 *                              executable, and arguments if they refer to files, must follow the convention of the
 *                              remote file system.
 *
 *      environment             The array of strings, each element of which has environment variable settings in the
 *                              format name=value, or null if the subprocess should inherit the environment of the
 *                              current process.
 *
 *      directory               The working directory of the subprocess, or null if the subprocess should inherit the
 *                              working directory of the current process. The directory must follow the convention of
 *                              the remote file system.
 *
 *      data                    The initial data chunk for stdin. This is optional.
 *
 *      eof                     The EOF indicator marks the end of stdin.
 *
 *      redirectErrorStream     Redirect stderr to the same stream as stdout.
 *
 */
public class ExecuteCommandRequest extends AbstractRemoteRequest {

    private String[] arguments;
    private boolean eof;
    private boolean redirectErrorStream;

    // Optional fields
    private String[] environment;
    private String directory;
    private ByteBuffer[] data;

    public ExecuteCommandRequest() {
        super(ExecuteCommandRequest.class.getSimpleName());
    }

    @Override
    public ByteBuffer[] getData() {
        return data;
    }

    @Override
    public void setData(ByteBuffer[] data) {
        this.data = data;
    }

    public String[] getArguments() {
        return arguments;
    }

    public void setArguments(String[] arguments) {
        this.arguments = arguments;
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
    }

    public boolean isEof() {
        return eof;
    }

    public void setEof(boolean eof) {
        this.eof = eof;
    }

    public boolean isRedirectErrorStream() {
        return redirectErrorStream;
    }

    public void setRedirectErrorStream(boolean redirectErrorStream) {
        this.redirectErrorStream = redirectErrorStream;
    }

    @Override
    public ServiceResponse execute(ServiceNexus nexus) {
        return nexus.getProtocolHandler(RemoteProtocolServer.class).executeCommand(this, nexus);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        arguments = new String[in.read()];

        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = in.readUTF();
        }

        if (in.readBoolean()) {
            environment = new String[in.read()];

            for (int i = 0; i < environment.length; i++) {
                environment[i] = in.readUTF();
            }
        }

        if (in.readBoolean()) {
            directory = in.readUTF();
        }

        eof = in.readBoolean();
        redirectErrorStream = in.readBoolean();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.write(arguments.length);

        for (String argument : arguments) {
            out.writeUTF(argument);
        }

        out.writeBoolean(environment != null);

        if (environment != null) {
            out.write(environment.length);

            for (String var : environment) {
                out.writeUTF(var);
            }
        }

        out.writeBoolean(directory != null);

        if (directory != null) {
            out.writeUTF(directory);
        }

        out.writeBoolean(eof);
        out.writeBoolean(redirectErrorStream);
    }

    @Override
    public String toString() {
        return String.format("%s args[]=%s env[]=%s dir=%s eof=%b redirectErrorStream=%b", super.toString(),
                Arrays.toString(arguments), Arrays.toString(environment), directory, eof, redirectErrorStream);
    }
}
