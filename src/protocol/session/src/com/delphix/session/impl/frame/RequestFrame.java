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
 * This class describes a request session frame.
 *
 *   o maxSlotIDInUse
 *
 * This field refers to the slot table usage for the channel the request is associated with. The information may be
 * used by the owner of the slot table for flow control.
 */
public abstract class RequestFrame extends SessionFrame {

    protected int maxSlotIDInUse;

    protected RequestFrame() {
        super();
    }

    @Override
    public boolean isRequest() {
        return true;
    }

    public int getMaxSlotIDInUse() {
        return maxSlotIDInUse;
    }

    public void setMaxSlotIDInUse(int maxSlotIDInUse) {
        this.maxSlotIDInUse = maxSlotIDInUse;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        maxSlotIDInUse = in.readInt();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeInt(maxSlotIDInUse);
    }
}
