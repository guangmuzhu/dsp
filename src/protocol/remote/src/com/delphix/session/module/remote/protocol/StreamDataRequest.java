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

import com.delphix.session.service.ServiceTaggedRequest;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.ByteBuffer;

/**
 * The StreamDataRequest is sent to transfer a logically contiguous block of data for a given stream identified by the
 * tag and the optional type. The stream tag is managed by the application logic. It is opaque to the protocol as long
 * as it is unique among outstanding streams. The type is used to differentiate multiple sub-streams if exist.
 *
 * The StreamDataRequest includes the following fields.
 *
 *      offset                  The file offset for the stream update.
 *
 *      data                    The data chunk of the stream update.
 *
 *      eof                     The EOF indicator marks the end of the transfer.
 *
 *      type                    The stream type if any.
 *
 *      sync                    The sync indicator serving as a hint that data up to this point in the stream should
 *                              be flushed to stable storage.
 */
public abstract class StreamDataRequest extends AbstractRemoteRequest implements ServiceTaggedRequest {

    public static final int MAX_REQUEST_OVERHEAD = 256;

    protected ByteBuffer[] data;
    protected long offset;
    protected boolean eof;
    protected int type;
    protected boolean sync;

    protected StreamDataRequest(String name) {
        super(name);
    }

    @Override
    public ByteBuffer[] getData() {
        return data;
    }

    @Override
    public void setData(ByteBuffer[] data) {
        this.data = data;
    }

    @Override
    public Object getTag() {
        return task;
    }

    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public boolean isEof() {
        return eof;
    }

    public void setEof(boolean eof) {
        this.eof = eof;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public boolean isSync() {
        return sync;
    }

    public void setSync(boolean sync) {
        this.sync = sync;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        offset = in.readLong();
        eof = in.readBoolean();
        type = in.readInt();
        sync = in.readBoolean();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeLong(offset);
        out.writeBoolean(eof);
        out.writeInt(type);
        out.writeBoolean(sync);
    }

    @Override
    public String toString() {
        return String.format("%s offset=%d eof=%b type=%d sync=%b", super.toString(), offset, eof, type, sync);
    }
}
