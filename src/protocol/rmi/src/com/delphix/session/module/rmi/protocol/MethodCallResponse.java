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

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class MethodCallResponse extends AbstractRmiResponse {

    private Object value;
    private boolean exception;

    public MethodCallResponse() {
        super(MethodCallResponse.class.getSimpleName());
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public Object getValue() {
        return value;
    }

    public void setException(boolean exception) {
        this.exception = exception;
    }

    public boolean getException() {
        return exception;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        value = in.readObject();
        exception = in.readBoolean();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeObject(value);
        out.writeBoolean(exception);
    }

    @Override
    public String toString() {
        return String.format("%s value=%s exception=%b", super.toString(), value.toString(), exception);
    }
}
