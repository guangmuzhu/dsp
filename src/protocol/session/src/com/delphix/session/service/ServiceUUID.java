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
import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * This class describes a UUID based service terminus.
 */
public class ServiceUUID extends AbstractTerminus {

    private UUID uuid;
    private String alias;
    private boolean ephemeral;

    protected ServiceUUID() {

    }

    public ServiceUUID(String alias) {
        this(UUID.randomUUID(), true, alias);
    }

    public ServiceUUID(UUID uuid, boolean ephemeral, String alias) {
        this.uuid = uuid;
        this.ephemeral = ephemeral;

        if (alias != null) {
            this.alias = alias;
        } else {
            this.alias = uuid.toString();
        }
    }

    @Override
    public boolean isUniversal() {
        return true;
    }

    @Override
    public boolean isEphemeral() {
        return ephemeral;
    }

    @Override
    public String getAlias() {
        return alias;
    }

    @Override
    public byte[] getBytes() {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);

        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());

        return buffer.array();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (!(obj instanceof ServiceUUID)) {
            return false;
        }

        ServiceUUID terminus = (ServiceUUID) obj;

        return uuid.equals(terminus.uuid) && alias.equals(terminus.alias) && ephemeral == terminus.ephemeral;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(uuid).append(alias).append(ephemeral).toHashCode();
    }

    @Override
    public String toString() {
        return uuid.toString();
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        uuid = UUID.fromString(in.readUTF());

        if (in.readBoolean()) {
            alias = in.readUTF();
        }

        ephemeral = in.readBoolean();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeUTF(uuid.toString());

        if (alias != null) {
            out.writeBoolean(true);
            out.writeUTF(alias);
        } else {
            out.writeBoolean(false);
        }

        out.writeBoolean(ephemeral);
    }

    public static ServiceUUID deserialize(ObjectInput in) throws IOException, ClassNotFoundException {
        ServiceUUID uuid = new ServiceUUID();
        uuid.readExternal(in);
        return uuid;
    }
}
