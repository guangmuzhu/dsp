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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class describes an identifier for a session exchange.
 *
 * The identifier is allocated from a global sequence of monotonically increasing integers. The ID sequence is of
 * four-byte width and therefore wraps around at 0xffffffff. Since we use the ID for comparison only and not its
 * mathematical value, we use a signed integer for storage.
 */
public class ExchangeID implements Externalizable {

    private static AtomicInteger xidSequence = new AtomicInteger();

    private int xid;

    private ExchangeID() {

    }

    private ExchangeID(int xid) {
        this.xid = xid;
    }

    public int getXid() {
        return xid;
    }

    public void setXid(int xid) {
        this.xid = xid;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (!(obj instanceof ExchangeID)) {
            return false;
        }

        ExchangeID exchangeID = (ExchangeID) obj;

        return xid == exchangeID.getXid();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(xid).toHashCode();
    }

    @Override
    public String toString() {
        return "[XID:" + String.format("0x%08x", xid) + "]";
    }

    public static ExchangeID allocate() {
        return new ExchangeID(xidSequence.incrementAndGet());
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        xid = in.readInt();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(xid);
    }

    public static ExchangeID deserialize(ObjectInput in) throws IOException, ClassNotFoundException {
        ExchangeID exchangeID = new ExchangeID();
        exchangeID.readExternal(in);
        return exchangeID;
    }
}
