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

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * This class describes an abstract service terminus which should be the base for all concrete service terminus. The
 * intention is to force all service terminus to override the enclosed methods.
 */
public abstract class AbstractTerminus implements ServiceTerminus {

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public abstract boolean equals(Object obj);

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public abstract int hashCode();

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public abstract String toString();

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        // This is called from each subclass even though there is nothing to do for now
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        TerminusType type = TerminusType.getType(this.getClass());
        out.writeByte(type.ordinal());
    }

    public static ServiceTerminus deserialize(ObjectInput in) throws IOException, ClassNotFoundException {
        TerminusType type = TerminusType.values()[in.readByte()];

        // newInstance uses reflection but not class loading
        ServiceTerminus terminus;

        try {
            terminus = type.getObjectClass().newInstance();
        } catch (Exception e) {
            throw new IOException(e);
        }

        terminus.readExternal(in);

        return terminus;
    }
}
