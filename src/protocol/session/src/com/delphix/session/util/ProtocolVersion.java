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

package com.delphix.session.util;

import org.apache.commons.lang.builder.HashCodeBuilder;

import java.io.*;

/**
 * This class describes the protocol version. It consists of three fields, namely, major, minor, and revision. It
 * supports comparison operations between two versions in the precedence order of these version fields.
 */
public class ProtocolVersion implements Externalizable {

    private static final ProtocolVersion RESERVED_VERSION = new ProtocolVersion(0xff, 0xff, 0xff);
    private static final String DELIMITER = ".";

    private int major;
    private int minor;
    private int revision;

    public ProtocolVersion() {

    }

    public ProtocolVersion(int major, int minor, int revision) {
        this.major = major;
        this.minor = minor;
        this.revision = revision;
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getRevision() {
        return revision;
    }

    public boolean greaterThan(ProtocolVersion version) {
        if (major > version.major) {
            return true;
        } else if (major < version.major) {
            return false;
        } else if (minor > version.minor) {
            return true;
        } else if (minor < version.minor) {
            return false;
        } else {
            return revision > version.revision;
        }
    }

    public boolean lessThan(ProtocolVersion version) {
        if (major < version.major) {
            return true;
        } else if (major > version.major) {
            return false;
        } else if (minor < version.minor) {
            return true;
        } else if (minor > version.minor) {
            return false;
        } else {
            return revision < version.revision;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (!(obj instanceof ProtocolVersion)) {
            return false;
        }

        ProtocolVersion version = (ProtocolVersion) obj;

        return major == version.major && minor == version.minor && revision == version.revision;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(major).append(minor).append(revision).toHashCode();
    }

    @Override
    public String toString() {
        return String.valueOf(major) + DELIMITER + String.valueOf(minor) + DELIMITER + String.valueOf(revision);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        major = in.readByte() & 0xff;
        minor = in.readByte() & 0xff;
        revision = in.readByte() & 0xff;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        write(out);
    }

    public static ProtocolVersion deserialize(ObjectInput in) throws IOException, ClassNotFoundException {
        ProtocolVersion version = new ProtocolVersion();
        version.readExternal(in);
        return version;
    }

    public void write(DataOutput out) throws IOException {
        out.writeByte(major);
        out.writeByte(minor);
        out.writeByte(revision);
    }

    public static ProtocolVersion getReserved() {
        return RESERVED_VERSION;
    }

    public static void writeReserved(DataOutput out) throws IOException {
        RESERVED_VERSION.write(out);
    }
}
