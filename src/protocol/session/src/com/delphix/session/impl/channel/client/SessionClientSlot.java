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

package com.delphix.session.impl.channel.client;

import com.delphix.session.impl.frame.SerialNumber;

/**
 * Each command sent in either channel over the session must be assigned a slot while it remains outstanding. There
 * are two pieces of information pertaining to each slot, referred to as SlotID and SlotSN in the frame definition,
 * carried in an exchange. The former serves to identify the slot while the latter the instance of command exchange
 * processed over the slot. The SlotSN is used by the owner of the slot table for response acknowledgment and cache
 * management. In addition to the slotID and the slotSN, the client slot also keeps track of the currently active
 * command and the last confirmed slotSN. The latter, as the name implies, refers to the most recent slotSN that is
 * known to be in sync with the server.
 */
public class SessionClientSlot {

    private final int slotID; // Slot ID
    private SerialNumber slotSN; // Current slot SN in use
    private SerialNumber lastSlotSN; // Last confirmed slot SN

    private SessionClientCommand command; // Current active command

    public SessionClientSlot(int slotID) {
        this.slotID = slotID;

        slotSN = SerialNumber.ZERO_SEQUENCE_INTEGER;
        lastSlotSN = slotSN;
    }

    public SessionClientCommand getCommand() {
        return command;
    }

    public void setCommand(SessionClientCommand command) {
        this.command = command;
    }

    public void resetCommand() {
        setCommand(null);
    }

    public int getSlotID() {
        return slotID;
    }

    public SerialNumber getSlotSN() {
        return slotSN;
    }

    /**
     * Check if the slot is in a confirmed state.
     */
    public boolean isConfirmed() {
        return slotSN == lastSlotSN;
    }

    /**
     * Advance the slot SN for the next command to be issued over this slot and save the last confirmed slot SN in
     * case the slot needs to be rolled back later.
     */
    public void advance() {
        lastSlotSN = slotSN;
        slotSN = slotSN.next();
    }

    /**
     * Confirm the slot SN advancement now that we have acknowledgment from the server.
     */
    public void confirm() {
        lastSlotSN = slotSN;
    }

    /**
     * The command failed on the server without advancing the slot SN so we need to roll it back.
     */
    public void rollback() {
        slotSN = lastSlotSN;
    }
}
