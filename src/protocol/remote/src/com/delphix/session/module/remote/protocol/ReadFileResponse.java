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

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.ByteBuffer;

/**
 * The ReadFileResponse is sent in response to the ReadFileRequest. It may include optional immediate data for small
 * file optimization.
 *
 * The ReadFileResponse includes the following fields.
 *
 *      length                  The total length of data sent in the transfer.
 *
 *      data                    The last data chunk of the transfer. This is optional.
 *
 *      offset                  The file offset for the data included. This is optional.
 */
public class ReadFileResponse extends AbstractRemoteResponse {

    private long length;

    // Optional fields
    private ByteBuffer[] data;
    private Long offset;

    public ReadFileResponse() {
        super(ReadFileResponse.class.getSimpleName());
    }

    public long getLength() {
        return length;
    }

    public void setLength(long length) {
        this.length = length;
    }

    @Override
    public ByteBuffer[] getData() {
        return data;
    }

    @Override
    public void setData(ByteBuffer[] data) {
        this.data = data;
    }

    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        length = in.readLong();

        if (in.readBoolean()) {
            offset = in.readLong();
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeLong(length);

        out.writeBoolean(offset != null);

        if (offset != null) {
            out.writeLong(offset);
        }
    }

    @Override
    public String toString() {
        return String.format("%s length=%d [offset=%d]", super.toString(), length, offset);
    }
}
