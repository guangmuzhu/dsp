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

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * This class serves as the base for all login response frames.
 *
 *   o status
 *
 * This fields refers to the login status. If the status is anything but successful, the other fields in the login
 * response should be ignored unless otherwise stated.
 */
public abstract class LoginResponse extends ResponseFrame {

    protected LoginStatus status;

    protected LoginResponse() {
        super();

        // Initialize the command sequences
        setCommandSN(SerialNumber.ZERO_SEQUENCE_INTEGER);
        setExpectedCommandSN(SerialNumber.ZERO_SEQUENCE_INTEGER);
    }

    @Override
    public boolean isForeChannel() {
        return true;
    }

    public LoginStatus getStatus() {
        return status;
    }

    public void setStatus(LoginStatus status) {
        this.status = status;
    }

    public abstract LoginPhase getPhase();

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        status = LoginStatus.values()[in.readByte()];
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeByte(status.ordinal());
    }
}
