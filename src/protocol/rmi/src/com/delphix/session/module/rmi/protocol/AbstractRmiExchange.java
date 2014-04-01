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
 * Copyright (c) 2014 by Delphix. All rights reserved.
 */

package com.delphix.session.module.rmi.protocol;

import com.delphix.session.service.ServiceExchange;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.ByteBuffer;

public abstract class AbstractRmiExchange implements ServiceExchange, Externalizable {

    protected String name;

    protected AbstractRmiExchange(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
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
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(name);
    }

    @Override
    public String toString() {
        return String.format("%s:", name);
    }
}
