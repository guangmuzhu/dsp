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

/**
 * The ReadFileRequest is sent to initiate incoming transfer of a given file. In general, data is received in a
 * series of StreamDataRequests sent by the file server after it receives the ReadFileRequest. However, it is
 * also possible to return data as part of the ReadFileResponse in order to optimize the transfer of small files.
 * The entire read transfer is strung together with the opaque stream identification tag. Multiple outstanding read
 * transfers may be supported as long as the tag is unique among them.
 *
 * The following diagram illustrates the protocol interactions for a file read transfer.
 *
 *      recipient                                                       sender
 *      ---------                                                       ------
 *
 *      ReadFileRequest                    ---->
 *
 *                                         <----                        StreamDataRequest {<EOF=false>}
 *      StreamDataResponse                 ---->
 *                                          ...
 *                                         <----                        StreamDataRequest {<EOF=true>}
 *      StreamDataResponse                 ---->
 *
 *                                         <----                        ReadFileResponse
 *
 * Failure scenarios for file read transfer are described as follows.
 *
 *      - If an error is detected on the data sender while processing ReadFileRequest, an exception is sent instead
 *        of a ReadFileResponse to terminate the read transfer.
 *
 *      - If an error is detected on the data sender during data transfer phase, outstanding StreamDataRequests
 *        are cancelled and an exception is sent instead of a ReadFileResponse to terminate the read transfer.
 *
 *      - If an error is detected on the data recipient while processing a StreamDataRequest, an exception will
 *        be sent instead of a StreamDataResponse, which will cause the data sender to terminate the read transfer.
 *
 *      - In case of failure detected on the data recipient from outside of the StreamDataRequest processing context,
 *        such as when we have to cancel the transfer, the outstanding ReadFileRequest is cancelled which results in
 *        an internal task management event delivered to the sender.
 *
 * The ReadFileRequest includes the following fields.
 *
 *      path                    The pathname leading to the file in the remote file system. The pathname must follow
 *                              the convention of the remote file system.
 *
 *      offset                  The file offset from which the read starts. If the offset is beyond the end of file,
 *                              no data shall be returned.
 *
 *      length                  The length of data desired. If length is zero, it indicates to the end of file. The
 *                              specified length may exceed the EOF, in which case, only the data available shall be
 *                              returned.
 *
 */
public class ReadFileRequest extends AbstractRemoteRequest {

    private String path;
    private long offset;
    private long length;

    public ReadFileRequest() {
        super(ReadFileRequest.class.getSimpleName());
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public long getLength() {
        return length;
    }

    public void setLength(long length) {
        this.length = length;
    }

    @Override
    public ServiceResponse execute(ServiceNexus nexus) {
        return nexus.getProtocolHandler(RemoteProtocolServer.class).readFile(this, nexus);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        path = in.readUTF();
        offset = in.readLong();
        length = in.readLong();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeUTF(path);
        out.writeLong(offset);
        out.writeLong(length);
    }

    @Override
    public String toString() {
        return String.format("%s path=%s offset=%d length=%d", super.toString(), path, offset, length);
    }
}
