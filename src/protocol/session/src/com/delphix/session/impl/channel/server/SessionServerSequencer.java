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
import com.delphix.session.impl.frame.SerialNumber;

import java.util.LinkedList;
import java.util.Queue;

/**
 * This class describes the command sequencer which ensures that commands are submitted for execution in the same
 * order as they arrive. It is made of a queue of commands with consecutive command sequence followed by a staging
 * array of commands with non-consecutive or scattered command sequence. As the gap in command sequence following
 * the last command in the queue is filled, the next batch of commands are moved from the array to the queue. The
 * queue of commands are ready to be submitted for execution. The staging array is a circular buffer bounded in
 * size by the maximum outstanding commands. A command sequence is mapped to the array slot by the sequence value
 * modulo the array length. The immediate next command sequence following the queued commands corresponds to the
 * expectedCommandSN.
 */
public class SessionServerSequencer {

    // Command queue with ordered commands waiting to be processed
    private final Queue<SessionServerCommand> pendingQueue = new LinkedList<SessionServerCommand>();

    private final SessionServerCommand[] commands; // Out-of-order command array

    private int head; // Next expected command slot
    private int size; // Number of out-of-order commands

    public SessionServerSequencer(SerialNumber commandSN, int capacity) {
        commands = new SessionServerCommand[capacity];
        head = toSlot(commandSN);
    }

    private int toSlot(SerialNumber commandSN) {
        return (int) (commandSN.getSerialNumber() % commands.length);
    }

    private int getDistance(int from, int to) {
        if (to > from) {
            return to - from;
        } else {
            return to + commands.length - from;
        }
    }

    /**
     * Enter the command and subject it to sequencing.
     */
    public int enter(SessionServerCommand command) {
        SerialNumber commandSN = command.getCommandSN();
        int pos = toSlot(commandSN);

        /*
         * Duplicate command, as a result of retry, should have been caught and handled earlier without being entered
         * into the sequencer. And sequencer array wrap around should never catch its own tail.
         */
        if (commands[pos] != null) {
            throw new ProtocolViolationException(commandSN + " conflicts " + commands[pos].getCommandSN());
        }

        size++;

        // Place the command in the array if it is not what we are expecting
        if (pos != head) {
            // Set the sequence order distance
            command.getStats().setOrderDistance(getDistance(head, pos));
            commands[pos] = command;
            return 0;
        }

        /*
         * We received what we expected in the command sequence. Offer it to the end of the pending queue as well as
         * any other commands that immediately follow it without any gap in the command sequence.
         */
        for (;;) {
            pendingQueue.offer(command);

            head++;
            size--;

            if (head == commands.length) {
                head = 0;
            }

            command = commands[head];

            if (command == null) {
                break;
            }

            commands[head] = null;
        }

        return getDistance(pos, head);
    }

    /**
     * Get the number of commands in the sequencer, including those in the pending queue and others that arrived out
     * of order and still waiting for sequence ordering to be satisfied.
     */
    public int size() {
        return size + pendingQueue.size();
    }

    /**
     * Get the maximum number of out of order commands the sequencer may hold before it runs out of space.
     */
    public int capacity() {
        return commands.length;
    }

    /**
     * Check if the sequencer has any command at all.
     */
    public boolean isEmpty() {
        return size == 0 && pendingQueue.isEmpty();
    }

    public Queue<SessionServerCommand> getPendingQueue() {
        return pendingQueue;
    }
}
