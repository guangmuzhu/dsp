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

package com.delphix.session.impl.channel.server;

import com.delphix.session.impl.frame.CommandStatus;
import com.delphix.session.impl.frame.SerialNumber;

/**
 * Each command sent in either channel over the session must be assigned a slot while it remains outstanding. There
 * are two pieces of information pertaining to each slot, referred to as SlotID and SlotSN in the frame definition,
 * carried in an exchange. The former serves to identify the slot while the latter the instance of command exchange
 * processed over the slot. The SlotSN is used by the owner of the slot table for response acknowledgment and cache
 * management. In addition to the slotID and the slotSN, the server slot also keeps track of any unconfirmed command
 * issued over the slot, including the currently executing command or the command that has been executed but not yet
 * confirmed.
 */
public class SessionServerSlot {

    private final int slotID; // Slot ID
    private SerialNumber slotSN; // Slot SN for the unconfirmed command

    private SessionServerCommand activeCommand; // Command currently being executed
    private SessionServerCommand cachedCommand; // Command executed but unconfirmed

    public SessionServerSlot(int slotID) {
        this.slotID = slotID;
        this.slotSN = SerialNumber.ZERO_SEQUENCE_INTEGER;
    }

    public int getSlotID() {
        return slotID;
    }

    public SerialNumber getSlotSN() {
        return slotSN;
    }

    public void setSlotSN(SerialNumber slotSN) {
        this.slotSN = slotSN;
    }

    public boolean reserve(SessionServerCommand command) {
        SerialNumber slotSN = command.getSlotSN();

        SerialNumber thisSN = this.slotSN;
        SerialNumber nextSN = thisSN.next();

        /*
         * In the normal case, the slotSN carried in the command is the next of the slotSN found in the slot. The new
         * command confirms the result of the last command executed over this slot, whether it is success or failure.
         * As a result, we can remove any trace of the last command from the channel.
         */
        if (slotSN.equals(nextSN)) {
            assert activeCommand == null;

            if (cachedCommand != null) {
                command.getChannel().evict(cachedCommand);
                cachedCommand = null;
            }

            activeCommand = command;

            command.setSlot(this);

            // Advance the slotSN after it has been confirmed
            this.slotSN = slotSN;

            return true;
        }

        /*
         * Duplicate commands should have been detected earlier using the exchange ID. The fact we got this far to
         * the slot table indicates the exchange ID of the current command doesn't match the one executed over this
         * slot last. Yet the slotSN is identical. That could only mean this is a false retry.
         */
        if (slotSN.equals(thisSN)) {
            command.setCommandStatus(CommandStatus.SLOT_FALSE_RETRY);
            return false;
        }

        /*
         * Otherwise the slot reservation failed. Notwithstanding software bugs, the most likely cause for this is
         * retry over a "stale" transport, in which case the peer won't see it.
         */
        command.setCommandStatus(CommandStatus.SLOT_SEQ_MISORDERED);

        return false;
    }

    public void release(SessionServerCommand command) {
        assert activeCommand == command;

        cachedCommand = activeCommand;
        activeCommand = null;
    }
}
