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

import com.delphix.session.impl.common.ProtocolViolationException;
import com.delphix.session.impl.frame.CommandStatus;

import java.util.Arrays;
import java.util.BitSet;

/**
 * Each of the dual channels in the session has its separate command slot table that is initialized during session
 * creation. The server is the owner of the slot table associated with the fore channel while the client the back
 * channel. The initial sizes of the two slot tables are advertised by the corresponding owners in the login process
 * during session creation. The size may be dynamically adjusted for resource management purposes with information
 * pertaining to flow control carried in the frames traveling in either direction.
 *
 * The owner of the slot table advertises the current size as well as the desired size, or CurrentMaxSlotID and
 * TargetMaxSlotID, respectively. In case of downsizing, the peer must respect the TargetMaxSlotID by stopping
 * assigning any slots higher than the TargetMaxSlotID immediately while quiescing outstanding commands issued over
 * those slots as soon as possible. Meanwhile, the owner of the slot table must ensure the peer is no longer using
 * any slots above the TargetMaxSlotID, including those that are in flight, before the slot table is trimmed. The
 * peer communicates the latest maxSlotIDInUse with each exchange as a hint to the server about the current slot
 * usage, which can be used to facilitate downsizing of the slot table. The following is a downsizing scheme.
 *
 * Once the owner decides to downsize the slot table due to resource constraint, each response sent to the peer will
 * carry the new TargetMaxSlotID, which is smaller than the CurrentMaxSlotID. For the first response sent over each
 * slot following the downsizing, a bit is set in an "announcement" bitmap. When a new request arrives from the peer
 * during the downsizing period, if the MaxSlotIDInUse is less than the TargetMaxSlotID _and_ the request slot falls
 * in the "announcement" bitmap, we can safely trim the slot table down to the TargetMaxSlotID. The reason this works
 * is because the peer promises never to allocate new slots above the TargetMaxSlotID once it learns of the change.
 * The next request that falls in the "announcement" bitmap confirms the peer's knowledge of the TargetMaxSlotID.
 */
public class SessionServerSlotTable {

    private SessionServerSlot[] table; // Slot table

    private int currentMaxSlotID; // Current max slot ID
    private int targetMaxSlotID; // Target max slot ID

    private int maxSlotIDInUse; // Max slot ID in use (according to peer)
    private int numInUse; // Number of slot IDs in use locally

    private BitSet announceMap; // Announcement bitmap (see above)

    public SessionServerSlotTable(int capacity) {
        table = new SessionServerSlot[capacity];

        for (int i = 0; i < capacity; i++) {
            table[i] = new SessionServerSlot(i);
        }

        currentMaxSlotID = capacity - 1;
        targetMaxSlotID = currentMaxSlotID;
    }

    public int getCurrentMaxSlotID() {
        return currentMaxSlotID;
    }

    public int getTargetMaxSlotID() {
        return targetMaxSlotID;
    }

    private void grow() {
        table = Arrays.copyOf(table, targetMaxSlotID);

        // Allocate new slots beyond the end of the original table
        for (int i = currentMaxSlotID + 1; i < table.length; i++) {
            table[i] = new SessionServerSlot(i);
        }

        currentMaxSlotID = targetMaxSlotID;
    }

    private void shrink() {
        table = Arrays.copyOf(table, targetMaxSlotID);
        announceMap = null;

        currentMaxSlotID = targetMaxSlotID;
    }

    public void setTargetMaxSlotID(int targetMaxSlotID) {
        // No change
        if (targetMaxSlotID == currentMaxSlotID) {
            return;
        }

        // Same change
        if (targetMaxSlotID == this.targetMaxSlotID) {
            return;
        }

        this.targetMaxSlotID = targetMaxSlotID;

        // Upsizing is immediate
        if (targetMaxSlotID > currentMaxSlotID) {
            grow();
            return;
        }

        /*
         * Downsizing has to wait until the "freeing" zone is cleared. We create an announcement bitmap (or clear it
         * in case a previous downsizing effort is still in progress) to keep track of announcement we will be making
         * to the peer.
         */
        if (announceMap == null) {
            announceMap = new BitSet(currentMaxSlotID + 1);
        } else {
            announceMap.clear();
        }
    }

    public int getMaxSlotIDInUse() {
        return maxSlotIDInUse;
    }

    public void setMaxSlotIDInUse(int slotID, int maxSlotIDInUse) {
        if (this.maxSlotIDInUse < maxSlotIDInUse) {
            this.maxSlotIDInUse = maxSlotIDInUse;
        }

        if (targetMaxSlotID < currentMaxSlotID) {
            /*
             * We are in the midst of downsizing. If the MaxSlotIDInUse is less than our target and the new request
             * falls in the announcement bitmap, it confirms the peer has the knowledge of the TargetMaxSlotID and
             * we can proceed with downsizing.
             */
            if (maxSlotIDInUse < targetMaxSlotID && announceMap.get(slotID)) {
                shrink();
            }
        }
    }

    public void reserve(SessionServerCommand command) {
        int slotID = command.getSlotID();
        int maxSlotIDInUse = command.getMaxSlotIDInUse();

        /*
         * The maxSlotIDInUse must be at least the slotID of the command if not greater, for the slot that is being
         * used by the command.
         */
        if (slotID > maxSlotIDInUse) {
            throw new ProtocolViolationException("invalid slot ID " + slotID + " exceeds " + maxSlotIDInUse);
        }

        /*
         * Neither the maxSlotIDInUse nor the slotID of the command must exceed the currentMaxSlotID for obvious
         * reasons. But this may not always constitute a protocol violation. When the server has to adjust the size
         * of the slot table downward due to flow control, the client may not have had the time to respond to that
         * or some lingering commands have arrived due to retry.
         */
        if (maxSlotIDInUse > currentMaxSlotID) {
            command.setCommandStatus(CommandStatus.SLOT_MAX_INVALID);
            return;
        }

        if (slotID > currentMaxSlotID) {
            command.setCommandStatus(CommandStatus.SLOT_ID_INVALID);
            return;
        }

        // Attempt to reserve the slot
        SessionServerSlot slot = table[slotID];

        if (!slot.reserve(command)) {
            return;
        }

        // Update the maxSlotIDInUse
        setMaxSlotIDInUse(slotID, maxSlotIDInUse);

        numInUse++;
    }

    public void release(SessionServerCommand command) {
        SessionServerSlot slot = command.getSlot();

        /*
         * If we are in the midst of downsizing, we'd like to record the slot in the announcement bitmap. Any future
         * requests arriving on this slot confirms the knowledge of the TargetMaxSlotID.
         */
        if (targetMaxSlotID < currentMaxSlotID) {
            announceMap.set(slot.getSlotID());
        }

        // Release the slot
        slot.release(command);

        numInUse--;
    }

    public int size() {
        return table.length;
    }

    public boolean isEmpty() {
        return numInUse == 0;
    }

    public int available() {
        return table.length - numInUse;
    }

    public int inUse() {
        return numInUse;
    }
}
