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

/**
 * The StreamDataResponse is sent in response to the StreamDataRequest for acknowledgment.
 *
 *      sync                    The sync indicator indicates whether the data included in the corresponding request
 *                              and anything before that in the stream have been flushed to stable storage.
 */
public class StreamDataResponse extends AbstractRemoteResponse {

    private boolean sync;

    public StreamDataResponse() {
        super(StreamDataResponse.class.getSimpleName());
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

        sync = in.readBoolean();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeBoolean(sync);
    }

    @Override
    public String toString() {
        return String.format("%s sync=%b", super.toString(), sync);
    }
}
