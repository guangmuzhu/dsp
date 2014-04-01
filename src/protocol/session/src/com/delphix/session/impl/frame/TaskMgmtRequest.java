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
 * This class describes the task management request.
 *
 * Task management is a tool used by the command issuer to abort a command that might otherwise be outstanding from
 * the issuer's perspective. Typically, a command is aborted due to timeout. It is possible that the abort is issued
 * for other reasons as well. The intention of task management is for command status synchronization. Specifically,
 * before the command is terminated by the issuer, it must ensure that the command is no longer "active" elsewhere,
 * being the server or the network.
 *
 *   o targetExchangeID
 *
 * This field refers to the ExchangeID of the command exchange to be aborted.
 *
 *   o targetCommandSequence
 *
 * This field refers to the CommandSequence of the command exchange to be aborted. It must be less than or equal to
 * the CommandSequence carried in the task management itself due to the fact the task management must be issued after
 * the command.
 */
public class TaskMgmtRequest extends OperateRequest {

    private SerialNumber targetCommandSN;
    private ExchangeID targetExchangeID;

    private int targetSlotID;
    private SerialNumber targetSlotSN;

    public TaskMgmtRequest() {
        super();
    }

    public SerialNumber getTargetCommandSN() {
        return targetCommandSN;
    }

    public void setTargetCommandSN(SerialNumber targetCommandSN) {
        this.targetCommandSN = targetCommandSN;
    }

    public ExchangeID getTargetExchangeID() {
        return targetExchangeID;
    }

    public void setTargetExchangeID(ExchangeID targetExchangeID) {
        this.targetExchangeID = targetExchangeID;
    }

    public int getTargetSlotID() {
        return targetSlotID;
    }

    public void setTargetSlotID(int targetSlotID) {
        this.targetSlotID = targetSlotID;
    }

    public SerialNumber getTargetSlotSN() {
        return targetSlotSN;
    }

    public void setTargetSlotSN(SerialNumber targetSlotSN) {
        this.targetSlotSN = targetSlotSN;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        targetExchangeID = ExchangeID.deserialize(in);
        targetCommandSN = SerialNumber.deserialize(in);

        targetSlotID = in.readInt();
        targetSlotSN = SerialNumber.deserialize(in);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        targetExchangeID.writeExternal(out);
        targetCommandSN.writeExternal(out);

        out.writeInt(targetSlotID);
        targetSlotSN.writeExternal(out);
    }
}
