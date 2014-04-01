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
 * This class describes a response session frame.
 *
 *   o targetMaxSlotID
 *   o currentMaxSlotID
 *
 * These fields refer to the slot table flow control settings for the channel the response is associated with. The
 * user of the slot table should make the adjustment according to the advertised settings in a timely manner.
 *
 * The owner of the slot table advertises the current as well as the desired size, referred to as currentMaxSlotID
 * and targetMaxSlotID, respectively. In case of downsizing, the peer must respect the targetMaxSlotID by stopping
 * assigning any slots higher than the targetMaxSlotID immediately while quiescing outstanding commands using those
 * slots.
 */
public abstract class ResponseFrame extends SessionFrame {

    protected int targetMaxSlotID;
    protected int currentMaxSlotID;

    protected ResponseFrame() {
        super();
    }

    @Override
    public boolean isRequest() {
        return false;
    }

    public int getTargetMaxSlotID() {
        return targetMaxSlotID;
    }

    public void setTargetMaxSlotID(int targetMaxSlotID) {
        this.targetMaxSlotID = targetMaxSlotID;
    }

    public int getCurrentMaxSlotID() {
        return currentMaxSlotID;
    }

    public void setCurrentMaxSlotID(int currentMaxSlotID) {
        this.currentMaxSlotID = currentMaxSlotID;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        currentMaxSlotID = in.readInt();
        targetMaxSlotID = in.readInt();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeInt(currentMaxSlotID);
        out.writeInt(targetMaxSlotID);
    }
}
