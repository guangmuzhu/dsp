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

import java.util.Arrays;
import java.util.BitSet;

public class SessionClientSlotTable {

    private static final int INVALID_SLOT_ID = -1;

    private SessionClientSlot[] table;
    private BitSet bitmap;

    private int targetMaxSlotID;

    public SessionClientSlotTable(int capacity) {
        table = new SessionClientSlot[capacity];

        for (int i = 0; i < capacity; i++) {
            table[i] = new SessionClientSlot(i);
        }

        bitmap = new BitSet(capacity);

        targetMaxSlotID = capacity - 1;
    }

    public SessionClientSlot reserve(SessionClientCommand command) {
        int pos = bitmap.nextClearBit(0);

        if (pos > targetMaxSlotID) {
            return null;
        }

        bitmap.set(pos);

        SessionClientSlot slot = table[pos];

        slot.advance();
        slot.setCommand(command);

        command.setSlot(slot);

        return slot;
    }

    public void release(SessionClientSlot slot) {
        int slotID = slot.getSlotID();

        // The slot must have been either confirmed or rolled back by now
        assert slot.isConfirmed();

        slot.getCommand().resetSlot();
        slot.resetCommand();

        bitmap.clear(slotID);

        // Attempt to shrink if the slot just released is in the "freeing" zone
        if (slotID > targetMaxSlotID) {
            shrink();
        }
    }

    private void shrink() {
        int slotID = bitmap.nextSetBit(targetMaxSlotID + 1);

        // We cannot shrink yet if there are still active slots in the "freeing" zone
        if (slotID >= 0) {
            return;
        }

        int capacity = targetMaxSlotID + 1;

        table = Arrays.copyOf(table, capacity);

        BitSet copy = new BitSet(capacity);

        // Operator and will ignore the extra bits in bitmap
        copy.set(0, capacity);
        copy.and(bitmap);

        bitmap = copy;
    }

    private void grow() {
        int oldCapacity = table.length;
        int capacity = targetMaxSlotID + 1;

        table = Arrays.copyOf(table, capacity);

        // Create the newly added slots
        for (int i = oldCapacity; i < capacity; i++) {
            table[i] = new SessionClientSlot(i);
        }

        BitSet copy = new BitSet(capacity);

        // Operator or will tack on the extra bits in the bitmap
        copy.or(bitmap);

        bitmap = copy;
    }

    /**
     * Get the maximum slot ID in use at the moment.
     */
    public int getMaxSlotIDInUse() {
        return bitmap.isEmpty() ? INVALID_SLOT_ID : bitmap.length() - 1;
    }

    /**
     * Return the capacity of the slot table.
     */
    public int capacity() {
        return table.length;
    }

    /**
     * Return the number of slots currently reserved.
     */
    public int size() {
        return bitmap.cardinality();
    }

    /**
     * Check if the slot table has any reserved slots.
     */
    public boolean isEmpty() {
        return bitmap.isEmpty();
    }

    /**
     * Check if the slot table has unreserved slots available.
     */
    public boolean available() {
        return capacity() > size();
    }

    public void update(int currentMaxSlotID, int targetMaxSlotID) {
        this.targetMaxSlotID = targetMaxSlotID;

        if (targetMaxSlotID + 1 > table.length) {
            grow();
        } else {
            shrink();
        }
    }
}
