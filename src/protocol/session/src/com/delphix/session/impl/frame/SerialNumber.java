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

/**
 * This describes the serial number as defined in RFC 1982.
 *
 * Serial numbers are formed from non-negative integers from a finite subset of the range of all integer values.  The
 * lowest integer in every subset used for this purpose is zero, the maximum is always one less than a power of two.
 *
 * When considered as serial numbers however no value has any particular significance, there is no minimum or maximum
 * serial number, every value has a successor and predecessor.
 *
 * To define a serial number to be used in this way, the size of the serial number space must be given.  This value,
 * called "SERIAL_BITS", gives the power of two which results in one larger than the largest integer corresponding to
 * a serial number value.  This also specifies the number of bits required to hold every possible value of a serial
 * number of the defined type.  The operations permitted upon serial numbers are defined in the following section.
 *
 * Only two operations are defined upon serial numbers, addition of a positive integer of limited range, and comparison
 * with another serial number.
 *
 * The simplest meaningful serial number space has SERIAL_BITS == 2. In this space, the integers that make up the
 * serial number space are 0, 1, 2, and 3. That is, 3 == 2 ^ SERIAL_BITS - 1. In this space, the largest integer that
 * it is meaningful to add to a sequence number is 2 ^ (SERIAL_BITS - 1) - 1, or 1. Then, as defined 0 + 1 == 1,
 * 1 + 1 == 2, 2 + 1 == 3, and 3 + 1 == 0. Further, 1 > 0, 2 > 1, 3 > 2, and 0 > 3. It is undefined whether 2 > 0 or
 * 0 > 2, and whether 1 > 3 or 3 > 1.
 */
public class SerialNumber implements Comparable<SerialNumber>, Externalizable {

    // Zero sequences that fit snugly into the corresponding signed java primitive type
    public static final SerialNumber ZERO_SEQUENCE_BYTE = new SerialNumber(7);
    public static final SerialNumber ZERO_SEQUENCE_SHORT = new SerialNumber(15);
    public static final SerialNumber ZERO_SEQUENCE_INTEGER = new SerialNumber(31);
    public static final SerialNumber ZERO_SEQUENCE_LONG = new SerialNumber(63);

    private static final int MINIMUM_SERIAL_BITS = 1;
    private static final int MAXIMUM_SERIAL_BITS = 63;
    private static final int DEFAULT_SERIAL_BITS = 31;

    private int serialBits;
    private long serialNumber;

    private SerialNumber() {

    }

    public SerialNumber(long serialNumber) {
        this(DEFAULT_SERIAL_BITS, serialNumber);
    }

    public SerialNumber(int serialBits) {
        this(serialBits, 0);
    }

    public SerialNumber(SerialNumber sn) {
        this(sn.serialBits, sn.serialNumber);
    }

    public SerialNumber(int serialBits, long serialNumber) {
        if (serialBits < MINIMUM_SERIAL_BITS || serialBits > MAXIMUM_SERIAL_BITS) {
            throw new IllegalArgumentException("serial bits " + serialBits + " out of range");
        }

        if (serialNumber < 0 || serialNumber > (1L << serialBits) - 1) {
            throw new IllegalArgumentException("serial number " + serialNumber + " out of range");
        }

        this.serialBits = serialBits;
        this.serialNumber = serialNumber;
    }

    public int getSerialBits() {
        return serialBits;
    }

    public long getSerialNumber() {
        return serialNumber;
    }

    private long serialAdd(long val) {
        if (val < 0 || val > (1L << (serialBits - 1)) - 1) {
            throw new IllegalArgumentException("additional value " + val + " out of range");
        }

        return (serialNumber + val) % (1L << serialBits);
    }

    private void increment(long val) {
        serialNumber = serialAdd(val);
    }

    public boolean isNext(SerialNumber sn) {
        return serialBits == sn.serialBits && serialNumber == sn.serialAdd(1);
    }

    public SerialNumber next() {
        return next(1);
    }

    public SerialNumber next(long val) {
        SerialNumber sn = new SerialNumber(this);
        sn.increment(val);
        return sn;
    }

    public boolean greaterThan(SerialNumber sn) {
        if (serialNumber < sn.serialNumber) {
            return sn.serialNumber - serialNumber > (1L << (serialBits - 1));
        } else if (serialNumber > sn.serialNumber) {
            return serialNumber - sn.serialNumber < (1L << (serialBits - 1));
        } else {
            return false;
        }
    }

    public boolean lessThan(SerialNumber sn) {
        if (serialNumber < sn.serialNumber) {
            return sn.serialNumber - serialNumber < (1L << (serialBits - 1));
        } else if (serialNumber > sn.serialNumber) {
            return serialNumber - sn.serialNumber > (1L << (serialBits - 1));
        } else {
            return false;
        }
    }

    public boolean greaterThanOrEqual(SerialNumber sn) {
        if (serialBits == sn.serialBits && serialNumber == sn.serialNumber) {
            return true;
        } else {
            return greaterThan(sn);
        }
    }

    public boolean lessThanOrEqual(SerialNumber sn) {
        if (serialBits == sn.serialBits && serialNumber == sn.serialNumber) {
            return true;
        } else {
            return lessThan(sn);
        }
    }

    @Override
    public int compareTo(SerialNumber sn) {
        if (greaterThan(sn)) {
            return 1;
        } else if (lessThan(sn)) {
            return -1;
        } else {
            return 0;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (!(obj instanceof SerialNumber)) {
            return false;
        }

        SerialNumber sn = (SerialNumber) obj;

        return serialBits == sn.serialBits && serialNumber == sn.serialNumber;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(serialBits).append(serialNumber).toHashCode();
    }

    @Override
    public String toString() {
        return "[SN:" + String.valueOf(serialNumber) + "]";
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        serialBits = in.readByte() & 0xff; // Unsigned byte

        if (serialBits < Byte.SIZE) {
            serialNumber = in.readByte();
        } else if (serialBits < Short.SIZE) {
            serialNumber = in.readShort();
        } else if (serialBits < Integer.SIZE) {
            serialNumber = in.readInt();
        } else {
            serialNumber = in.readLong();
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeByte(serialBits);

        if (serialBits < Byte.SIZE) {
            out.writeByte((int) serialNumber);
        } else if (serialBits < Short.SIZE) {
            out.writeShort((int) serialNumber);
        } else if (serialBits < Integer.SIZE) {
            out.writeInt((int) serialNumber);
        } else {
            out.writeLong(serialNumber);
        }
    }

    public static SerialNumber deserialize(ObjectInput in) throws IOException, ClassNotFoundException {
        SerialNumber sn = new SerialNumber();
        sn.readExternal(in);
        return sn;
    }
}
