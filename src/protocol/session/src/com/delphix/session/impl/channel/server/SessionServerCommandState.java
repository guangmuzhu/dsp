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

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Command State Machine
 * =====================
 *
 *    +----+---------+
 *    | S1 | INITIAL |
 *    +----+---------+
 *                 +
 *              T1 |
 *                 |
 *                 v
 *    +----+---------+ T2  +----+---------+     +----+---------+
 *    | S2 | PENDING |+--->| S3 | ACTIVE  |     | S7 |  RETRY  |
 *    +----+---------+     +----+---------+     +----+---------+
 *      +                    +          +          ^         +            +----+----+----+----+----+----+----+----+
 *      |                    | T8       |          |         |            | S1 | S2 | S3 | S4 | S5 | S6 | S7 | S8 |
 *      |                    |       T3 |       T5 |     T10 |       -----+----+----+----+----+----+----+----+----+
 *      |                    v          v          +         |        S1  | -  | T1 | -  | -  | -  | -  | -  | -  |
 *      |        +----+---------+ T4  +----+---------+       |       -----+----+----+----+----+----+----+----+----+
 *      |        | S5 |  FINAL  |<---+| S4 | INDOUBT |<------+        S2  | -  | -  | T2 | -  | -  | T7 | -  | -  |
 *      |        +----+---------+     +----+---------+               -----+----+----+----+----+----+----+----+----+
 *      |          ^                    +                             S3  | -  | -  | -  | T3 | T8 | -  | -  | -  |
 *      |          |                    |                            -----+----+----+----+----+----+----+----+----+
 *      |       T9 |                 T6 |                             S4  | -  | -  | -  | -  | T4 | T6 | T5 | -  |
 *      |          +                    |                            -----+----+----+----+----+----+----+----+----+
 *      |        +----+---------+       |                             S5  | -  | -  | -  | -  | -  | -  | -  | -  |
 *      |        | S8 | ABORTED |       |                            -----+----+----+----+----+----+----+----+----+
 *      |        +----+---------+       |                             S6  | -  | -  | -  | -  | -  | -  | -  | T11|
 *      |          ^          +         |                            -----+----+----+----+----+----+----+----+----+
 *      |      T11 |          | T12     |                             S7  | -  | -  | -  | T10| -  | -  | -  | -  |
 *      |          +          v         |                            -----+----+----+----+----+----+----+----+----+
 *      |        +----+---------+       |                             S8  | -  | -  | -  | -  | T9 | T12| -  | -  |
 *      +------->| S6 |  ABORT  |<------+                            -----+----+----+----+----+----+----+----+----+
 *         T7    +----+---------+
 *
 *                  State Transition Diagram                                   State Transition Matrix
 *
 *
 * Command State Descriptions
 *
 *    -S1: INITIAL    Command state after instantiation.
 *
 *    -S2: PENDING    Command is waiting for its turn to be processed, either executed or aborted, subject to sequence
 *                    ordering.
 *
 *    -S3: ACTIVE     Command is being executed or response is being sent. While the response is being sent, the
 *                    command cannot be aborted to ensure the task management response could never get ahead of the
 *                    command response.
 *
 *    -S4: INDOUBT    Command state after response is (re)sent. While in this state, a command is quiesced and waiting
 *                    for confirmation or acknowledgment from the client.
 *
 *    -S5: FINAL      Command is ready to be finalized and purged from the channel. Command enters this state after
 *                    it received confirmation from the client about the status of the command, whether response has
 *                    been received or aborted.
 *
 *    -S6: ABORT      Command state after a task management has been requested on it. Task management response is sent
 *                    immediately upon request unless the command is in the PENDING or ACTIVE state.
 *
 *    -S7: RETRY      Command is being retried, i.e., the response is being resent in response to a retry. While the
 *                    response is being sent, the command cannot be aborted to ensure the task management response
 *                    could never get ahead of the command response.
 *
 *    -S8: ABORTED    Command state after task management has been requested and responded to. While in this state,
 *                    a command is quiesced and waiting for confirmation or acknowledgment from the client.
 *
 *
 * State Transition descriptions
 *
 *    -T1:            Command enqueued pending sequence ordering.
 *
 *    -T2:            Command activated for execution.
 *
 *    -T3:            Command completed and response sent over the active session transport.
 *
 *    -T4:            Command status acknowledged by the client.
 *
 *    -T5:            Command reactivated after being retried over a different transport. Command just needs to be
 *                    queued for response.
 *
 *    -T6:            Command has task management requested while it is waiting for confirmation.
 *
 *    -T7:            Command has task management requested while it is pending.
 *
 *    -T8:            Command has encountered a slot failure and must be terminated immediately without caching.
 *
 *    -T9:            Command status acknowledged or it has encountered a slot failure after it is aborted.
 *
 *    -T10:           Command response resent.
 *
 *    -T11:           Command has task management request responded to.
 *
 *    -T12:           Command has task management requested again.
 */
public enum SessionServerCommandState {

    INITIAL,
    PENDING,
    ACTIVE,
    INDOUBT,
    FINAL,
    ABORT,
    RETRY,
    ABORTED;

    private static Map<SessionServerCommandState, EnumSet<SessionServerCommandState>> fsm;

    static {
        fsm = new HashMap<SessionServerCommandState, EnumSet<SessionServerCommandState>>();

        // Command FSM definition
        fsm.put(null, EnumSet.of(INITIAL));
        fsm.put(INITIAL, EnumSet.of(PENDING));
        fsm.put(PENDING, EnumSet.of(ACTIVE, ABORT));
        fsm.put(ACTIVE, EnumSet.of(INDOUBT, FINAL));
        fsm.put(INDOUBT, EnumSet.of(RETRY, ABORT, FINAL));
        fsm.put(FINAL, EnumSet.noneOf(SessionServerCommandState.class));
        fsm.put(ABORTED, EnumSet.of(ABORT, FINAL));
        fsm.put(RETRY, EnumSet.of(INDOUBT));
        fsm.put(ABORT, EnumSet.of(ABORTED));
    }

    /**
     * Validate if the specified session command state is reachable from the current state according to the formal
     * definition of the session command finite state machine.
     */
    public static void validate(SessionServerCommandState oldState, SessionServerCommandState newState) {
        EnumSet<SessionServerCommandState> states = fsm.get(oldState);

        if (!states.contains(newState)) {
            throw new IllegalStateException("illegal transition from " + oldState + " to " + newState);
        }
    }
}
