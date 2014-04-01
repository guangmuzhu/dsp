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

package com.delphix.session.impl.server;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Session State Machine
 * =====================
 *
 *                                   +----+-----------+
 *                                   | Q1 |    FREE   |
 *                                   +----+-----------+
 *                                     ^            +
 *                                     | N9      N1 |
 *                                     +            v
 *                                   +----+-----------+
 *                                   | Q2 |   ACTIVE  |
 *                                   +----+-----------+
 *                                                  +
 *                                               N2 |
 *                                                  v                          +----+----+----+----+----+----+
 *      N6  +----+-----------+  N3   +----+-----------+                        |Q1  |Q2  |Q3  |Q4  |Q5  |Q6  |
 *    +---->| Q6 |   ZOMBIE  |<------| Q3 | LOGGED_IN |                   -----+----+----+----+----+----+----+
 *    |     +----+-----------+       +----+-----------+                    Q1  | -  |N1  | -  | -  | -  | -  |
 *    |       ^                        ^            +                     -----+----+----+----+----+----+----+
 *    |   N11 |                        |            |                      Q2  | N9 | -  |N2  | -  | -  | -  |
 *    |       +                        |            |                     -----+----+----+----+----+----+----+
 *    |     +----+-----------+  N10    |            |                      Q3  | -  | -  | -  |N5  | -  |N3  |
 *    |     | Q5 | IN_CONTIN |+--------+            |                     -----+----+----+----+----+----+----+
 *    |     +----+-----------+                      |                      Q4  | -  | -  | -  | -  |N7  |N6  |
 *    |       +            ^                        |                     -----+----+----+----+----+----+----+
 *    |    N8 |            | N7                     |                      Q5  | -  | -  |N10 |N8  | -  |N11 |
 *    |       v            +                        |                     -----+----+----+----+----+----+----+
 *    |     +----+-----------+                      |                      Q6  | -  | -  | -  | -  | -  | -  |
 *    +-----| Q4 |   FAILED  |<---------------------+                     -----+----+----+----+----+----+----+
 *          +----+-----------+  N5
 *
 *        State Transition Diagram                                                State Transition Matrix
 *
 *
 * Session State Descriptions
 *
 *    -Q1: FREE       State on instantiation.
 *
 *    -Q2: ACTIVE     The first connection in the session transitioned to IN_LOGIN, waiting for it to complete the
 *                    login process.
 *
 *    -Q3: LOGGED_IN  Waiting for all session events.
 *
 *    -Q4: FAILED     Waiting for session recovery or session continuation.
 *
 *    -Q5: IN_CONTIN  Waiting for session continuation attempt to reach a conclusion.
 *
 *    -Q6: ZOMBIE     State after reset.
 *
 *
 * State Transition Descriptions
 *
 *    -N1:            The first connection in the session had reached the IN_LOGIN state.
 *
 *    -N2:            At least one connection reached the LOGGED_IN state.
 *
 *    -N3:            Graceful closing of the session via session closure, a successful session reinstatement
 *                    cleanly closed the session, or internal session reset due to timeout or other reasons. This
 *                    results in the freeing of all session states.
 *
 *    -N5:            Session failure occurred whence the last operational transport has been closed due to abnormal
 *                    causes and all outstanding commands start to wait for recovery.
 *
 *    -N6:            Internal session reset due to timeout or other reasons, or session reinstatement cleared this
 *                    session instance. This results in the freeing of all session states.
 *
 *    -N7:            A session continuation attempt is initiated.
 *
 *    -N8:            The last session continuation attempt failed.
 *
 *    -N9:            Login attempt on the leading connection failed.
 *
 *   -N10:            A session continuation attempt succeeded.
 *
 *   -N11:            Internal session reset due to timeout or other reasons, or session reinstatement cleared this
 *                    session instance. This results in the freeing of all session states.
 */
public enum ServerSessionState {

    FREE,
    ACTIVE,
    LOGGED_IN,
    FAILED,
    IN_CONTINUE,
    ZOMBIE;

    private static Map<ServerSessionState, EnumSet<ServerSessionState>> fsm;

    static {
        fsm = new HashMap<ServerSessionState, EnumSet<ServerSessionState>>();

        // Session FSM definition
        fsm.put(null, EnumSet.of(FREE));
        fsm.put(FREE, EnumSet.of(ACTIVE));
        fsm.put(ACTIVE, EnumSet.of(FREE, LOGGED_IN));
        fsm.put(LOGGED_IN, EnumSet.of(FAILED, ZOMBIE));
        fsm.put(FAILED, EnumSet.of(IN_CONTINUE, ZOMBIE));
        fsm.put(IN_CONTINUE, EnumSet.of(LOGGED_IN, FAILED, ZOMBIE));
        fsm.put(ZOMBIE, EnumSet.noneOf(ServerSessionState.class));
    }

    /**
     * Validate if the specified session state is reachable from the current state according to the formal definition
     * of the session finite state machine.
     */
    public static void validate(ServerSessionState oldState, ServerSessionState newState) {
        EnumSet<ServerSessionState> states = fsm.get(oldState);

        if (!states.contains(newState)) {
            throw new IllegalStateException("illegal transition from " + oldState + " to " + newState);
        }
    }
}
