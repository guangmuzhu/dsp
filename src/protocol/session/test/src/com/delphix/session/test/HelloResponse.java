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

package com.delphix.session.test;

import com.delphix.session.service.ServiceResponse;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.ByteBuffer;

public class HelloResponse implements ServiceResponse, Externalizable {

    private String message = "hey back";
    private ByteBuffer[] data;

    public HelloResponse() {

    }

    public HelloResponse(byte[]... sg) {
        data = new ByteBuffer[sg.length];

        for (int i = 0; i < sg.length; i++) {
            data[i] = ByteBuffer.wrap(sg[i]);
        }
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return getMessage();
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        message = in.readUTF();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(message);
    }

    @Override
    public ByteBuffer[] getData() {
        return data;
    }

    @Override
    public void setData(ByteBuffer[] data) {
        this.data = data;
    }
}
