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

import com.delphix.session.service.ServiceExchange;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.ByteBuffer;

/**
 * This defines the root of the protocol exchange hierarchy for all exchanges defined in the service module. It
 * implements the "marker" interfaces, such as ServiceExchange and Externalizable (the latter is specific to the
 * codec chosen), and includes properties common to all exchanges.
 *
 * The protocol definition for the service follows the hierarchy illustrated below.
 *
 *      AbstractRemoteExchange
 *          |
 *          + AbstractRemoteRequest
 *          |     |
 *          |     + ReadFileRequest
 *          |     |
 *          |     + ...
 *          |
 *          + AbstractRemoteResponse
 *                |
 *                + ReadFileResponse
 *                |
 *                + ...
 *
 * The AbstractRemoteExchange includes the following fields.
 *
 *      name                    The name of the protocol exchange used for debugging purpose.
 *
 *      task                    Each protocol exchange defined in this service module is issued in the context of
 *                              a task. The task tag is included here for easy identification.
 */
public abstract class AbstractRemoteExchange implements ServiceExchange, Externalizable {

    protected String name;
    protected int task;

    protected AbstractRemoteExchange(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public int getTask() {
        return task;
    }

    public void setTask(int task) {
        this.task = task;
    }

    public ByteBuffer[] getData() {
        return null;
    }

    public void setData(ByteBuffer[] data) {
        throw new UnsupportedOperationException("setData not supported");
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        name = in.readUTF();
        task = in.readInt();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(name);
        out.writeInt(task);
    }

    @Override
    public String toString() {
        return String.format("%s: task=%d", name, task);
    }
}
