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

/**
 * The WriteFileRequest is sent to initiate outgoing transfer of a given file. In general, data is sent in a series
 * of StreamDataRequests after the WriteFileRequest is sent. However, it is also possible to include data in the
 * WriteFileRequest itself to optimize for the transfer of small files. The entire write transfer is strung together
 * with the opaque stream identification tag. Multiple outstanding write transfers may be supported as long as the
 * tag is unique.
 *
 * The following diagram illustrates the protocol interactions for a file write transfer.
 *
 *      sender                                                          recipient
 *      ------                                                          ---------
 *
 *      WriteFileRequest                   ---->
 *
 *                                         <----                        StreamStartRequest
 *      StreamStartResponse                ---->
 *
 *      StreamDataRequest {<EOF=false>}    ---->
 *                                         <----                        StreamDataResponse
 *                                          ...
 *      StreamDataRequest {<EOF=true>}     ---->
 *                                         <----                        StreamDataResponse
 *
 *                                         <----                        WriteFileResponse
 *
 * Failure scenarios for file write transfer are described as follows.
 *
 *      - If an error is detected on the data recipient while processing WriteFileRequest, an exception is sent
 *        instead of a WriteFileResponse to terminate the write transfer.
 *
 *      - If an error is detected on the data sender while processing StreamStartRequest, an exception is sent
 *        instead of StreamStartResponse and the write transfer is terminated by the data recipient.
 *
 *      - If an error is detected on the data sender during data transfer phase, outstanding StreamDataRequests
 *        are cancelled followed by the WriteFileRequest itself.
 *
 *      - If an error is detected on the data recipient while processing a StreamDataRequest, an exception will
 *        be sent instead of a StreamDataResponse. Upon receiving the exception, the data sender shall terminate
 *        the write transfer as described above.
 *
 * The WriteFileRequest includes the following fields.
 *
 *      path                    The pathname leading to the destination file in the remote file system. The pathname
 *                              must follow the convention of the remote file system.
 *
 *      mode                    The access modes of the destination file.
 *
 *      offset                  The file offset from which the write is to start.
 *
 *      length                  The total length of the data to be written in the transfer. Zero indicates the size
 *                              is unknown upfront. EOF will be used to end the transfer.
 *
 *      data                    The initial data chunk of the transfer. This is optional.
 *
 *      eof                     The EOF indicator marks the end of the transfer.
 *
 */
public class WriteFileRequest extends AbstractRemoteRequest {

    private String path;
    private int mode;
    private long offset;
    private long length;
    private boolean eof;

    // Optional fields
    private ByteBuffer[] data;

    public WriteFileRequest() {
        super(WriteFileRequest.class.getSimpleName());
    }

    @Override
    public ByteBuffer[] getData() {
        return data;
    }

    @Override
    public void setData(ByteBuffer[] data) {
        this.data = data;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
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

    public boolean isEof() {
        return eof;
    }

    public void setEof(boolean eof) {
        this.eof = eof;
    }

    @Override
    public ServiceResponse execute(ServiceNexus nexus) {
        return nexus.getProtocolHandler(RemoteProtocolServer.class).writeFile(this, nexus);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        path = in.readUTF();
        mode = in.read();
        offset = in.readLong();
        length = in.readLong();
        eof = in.readBoolean();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeUTF(path);
        out.write(mode);
        out.writeLong(offset);
        out.writeLong(length);
        out.writeBoolean(eof);
    }

    @Override
    public String toString() {
        return String.format("%s path=%s mode=%x offset=%d length=%d [eof=%b]", super.toString(),
                path, mode, offset, length, eof);
    }
}
