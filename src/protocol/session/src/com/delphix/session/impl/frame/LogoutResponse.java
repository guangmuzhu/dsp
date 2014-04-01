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
 * This class describes the logout response.
 *
 *   o status
 *
 * This field indicates the logout status.
 */
public class LogoutResponse extends OperateResponse {

    private LogoutStatus status;

    public LogoutResponse() {
        super();
    }

    @Override
    public boolean isForeChannel() {
        return true;
    }

    public LogoutStatus getStatus() {
        return status;
    }

    public void setStatus(LogoutStatus status) {
        this.status = status;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        status = LogoutStatus.values()[in.readByte()];
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeByte(status.ordinal());
    }
}
