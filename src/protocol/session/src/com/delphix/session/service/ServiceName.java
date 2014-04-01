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

package com.delphix.session.service;

import org.apache.commons.lang.builder.HashCodeBuilder;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * This describes a simple name based service terminus.
 */
public class ServiceName extends AbstractTerminus {

    private String name;
    private boolean ephemeral;

    protected ServiceName() {

    }

    public ServiceName(String name, boolean ephemeral) {
        this.name = name;
        this.ephemeral = ephemeral;
    }

    @Override
    public boolean isUniversal() {
        return false;
    }

    @Override
    public boolean isEphemeral() {
        return ephemeral;
    }

    @Override
    public String getAlias() {
        return name;
    }

    @Override
    public byte[] getBytes() {
        return name.getBytes();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (!(obj instanceof ServiceName)) {
            return false;
        }

        ServiceName terminus = (ServiceName) obj;

        return name.equals(terminus.name) && ephemeral == terminus.ephemeral;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(name).append(ephemeral).toHashCode();
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        name = in.readUTF();
        ephemeral = in.readBoolean();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeUTF(name);
        out.writeBoolean(ephemeral);
    }

    public static ServiceName deserialize(ObjectInput in) throws IOException, ClassNotFoundException {
        ServiceName name = new ServiceName();
        name.readExternal(in);
        return name;
    }
}
