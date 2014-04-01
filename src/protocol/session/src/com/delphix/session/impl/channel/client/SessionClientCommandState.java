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

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Command State Machine
 * =====================
 *
 *                  +----+---------+
 *                  | S1 | INITIAL |
 *                  +----+---------+
 *                    +         +
 *                 T3 |         | T1
 *                    |         |
 *                    v         v
 *       +----+---------+ T4  +----+---------+
 *       | S3 | PENDING |+--->| S2 | ACTIVE  |+------------+
 *       +----+---------+     +----+---------+             |
 *                    +         +          ^               |
 *                T10 |         | T2       | T5         T6 |
 *                    |         |       T9 |               |
 *                    v         v          v               |
 *                 +----+---------+     +----+---------+   |            +----+----+----+----+----+----+----+
 *                 | S4 |  FINAL  |<---+| S5 |  RETRY  |   |            | S1 | S2 | S3 | S4 | S5 | S6 | S7 |
 *                 +----+---------+ T13 +----+---------+   |       -----+----+----+----+----+----+----+----+
 *                              ^          +               |        S1  | -  | T1 | T3 | -  | -  | -  | -  |
 *                              |          | T7            |       -----+----+----+----+----+----+----+----+
 *                              |          |               |        S2  | -  | -  | -  | T2 | T5 | -  | T6 |
 *                              |          v               |       -----+----+----+----+----+----+----+----+
 *                              |       +----+---------+   |        S3  | -  | T4 | -  | T10| -  | -  | -  |
 *                              |       | S6 | INDOUBT |   |       -----+----+----+----+----+----+----+----+
 *                              |       +----+---------+   |        S4  | -  | -  | -  | -  | -  | -  | -  |
 *                              |          +         ^     |       -----+----+----+----+----+----+----+----+
 *                              |      T11 |         |     |        S5  | -  | T9 | -  | T13| -  | T7 | -  |
 *                           T8 |          |     T12 |     |       -----+----+----+----+----+----+----+----+
 *                              |          v         +     |        S6  | -  | -  | -  | -  | -  | -  | T11|
 *                              |       +----+---------+   |       -----+----+----+----+----+----+----+----+
 *                              +------+| S7 |  ABORT  |<--+        S7  | -  | -  | -  | T8 | -  | T12| -  |
 *                                      +----+---------+           -----+----+----+----+----+----+----+----+
 *
 *
 *                  State Transition Diagram                               State Transition Matrix
 *
 *
 * Command State Descriptions
 *
 *    -S1: INITIAL    Command state after instantiation.
 *
 *    -S2: ACTIVE     Command is in flight over one of the session transports attached to the channel.
 *
 *    -S3: PENDING    Command is waiting for channel to become ready to accept incoming commands. Pending commands do
 *                    not modify session state until processed and they are processed in the arrival order.
 *
 *    -S4: FINAL      Command is done as far as the session is concerned and is ready to be completed back up to the
 *                    application. Session state, such as commandSN and slot, must have been synchronized with the
 *                    server, if modified.
 *
 *    -S5: RETRY      Command is waiting to be retried after it has failed due to transport reset. A command in this
 *                    state holds on to the slot and commandSN previously assigned.
 *
 *    -S6: INDOUBT    Command is waiting for the task management to be initiated. Command has been quiesced before
 *                    entering this state so command response will not be received.
 *
 *    -S7: ABORT      Command is being aborted. A task management has been initiated on behalf of the command to bring
 *                    the session state in sync between the client and server. Command response may still be received
 *                    while in this state.
 *
 *
 * State Transition descriptions
 *
 *    -T1:            Command processed immediately upon arrival and request sent over one of the operational session
 *                    transports.
 *
 *    -T2:            Command completed and response received from the session transport.
 *
 *    -T3:            Command enqueued pending session flow control.
 *
 *    -T4:            Command processing restarted after the command slot becomes available.
 *
 *    -T5:            Command failed due to transport reset and is waiting to be retried over a different transport.
 *
 *    -T6:            Command is being aborted while it is in flight on a transport. A task management is queued on
 *                    on behalf of the command.
 *
 *    -T7:            Command is being aborted while it is waiting for retry over a different transport than the one
 *                    it last failed on. Command is retried no more and a task management is queued on behalf of it.
 *
 *    -T8:            Command may have either completed on its own or been aborted by the task management. Either way,
 *                    the session state is in sync.
 *
 *    -T9:            Command is being retried over another transport after previous failure(s).
 *
 *    -T10:           Command is aborted or reset while it is pending session flow control.
 *
 *    -T11:           A previously queued task management is initiated. The task management is sent over the same
 *                    transport as the command if the latter is still active. Otherwise, it will be issued over any
 *                    available transport if the command hasn't completed on its own.
 *
 *    -T12:           A previously initiated task management failed and a new one has been queued.
 *
 *    -T13:           Command is reset while waiting for retry.
 */
public enum SessionClientCommandState {

    INITIAL,
    ACTIVE,
    PENDING,
    FINAL,
    RETRY,
    ABORT,
    INDOUBT;

    private static Map<SessionClientCommandState, EnumSet<SessionClientCommandState>> fsm;

    static {
        fsm = new HashMap<SessionClientCommandState, EnumSet<SessionClientCommandState>>();

        // Command FSM definition
        fsm.put(null, EnumSet.of(INITIAL));
        fsm.put(INITIAL, EnumSet.of(ACTIVE, PENDING));
        fsm.put(ACTIVE, EnumSet.of(FINAL, RETRY, ABORT));
        fsm.put(PENDING, EnumSet.of(ACTIVE, FINAL));
        fsm.put(FINAL, EnumSet.noneOf(SessionClientCommandState.class));
        fsm.put(RETRY, EnumSet.of(ACTIVE, INDOUBT, FINAL));
        fsm.put(ABORT, EnumSet.of(INDOUBT, FINAL));
        fsm.put(INDOUBT, EnumSet.of(ABORT));
    }

    /**
     * Validate if the specified session command state is reachable from the current state according to the formal
     * definition of the session command finite state machine.
     */
    public static void validate(SessionClientCommandState oldState, SessionClientCommandState newState) {
        EnumSet<SessionClientCommandState> states = fsm.get(oldState);

        if (!states.contains(newState)) {
            throw new IllegalStateException("illegal transition from " + oldState + " to " + newState);
        }
    }
}
