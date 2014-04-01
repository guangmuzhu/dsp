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

package com.delphix.session.impl.frame;

import org.apache.commons.lang.builder.HashCodeBuilder;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;

/**
 * This describes a handle to the session created by the server on behalf of the client. The handle remains opaque
 * except for the server who created it. The only supported operations on the handle are comparison and hash code.
 */
public class SessionHandle implements Externalizable {

    private byte[] handle;

    private SessionHandle() {

    }

    public SessionHandle(byte[] handle) {
        this.handle = handle;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (!(obj instanceof SessionHandle)) {
            return false;
        }

        SessionHandle handle = (SessionHandle) obj;

        return Arrays.equals(this.handle, handle.handle);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(handle).toHashCode();
    }

    @Override
    public String toString() {
        return "[HDL:" + Arrays.toString(handle) + "]";
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        handle = new byte[in.readInt()];
        in.read(handle);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(handle.length);
        out.write(handle);
    }

    public static SessionHandle deserialize(ObjectInput in) throws IOException, ClassNotFoundException {
        SessionHandle handle = new SessionHandle();
        handle.readExternal(in);
        return handle;
    }
}
