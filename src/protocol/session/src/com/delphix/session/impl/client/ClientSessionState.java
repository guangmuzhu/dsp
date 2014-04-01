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

package com.delphix.session.impl.client;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Session State Machine
 * =====================
 *
 *                                      +----+-----------+
 *                                      | Q1 |    FREE   |
 *                                      +----+-----------+
 *                                        |
 *                                     N2 |
 *                                        v
 *                                      +----+-----------+
 *                         +------------| Q2 |   ACTIVE  |
 *                         |            +----+-----------+                  +----+----+----+----+----+
 *                         |              |                                 |Q1  |Q2  |Q3  |Q4  |Q6  |
 *                      N7 |           N1 |                            -----+----+----+----+----+----+
 *                         v              v                             Q1  | -  |N2  | -  | -  | -  |
 *          +----+-----------+    N3    +----+-----------+             -----+----+----+----+----+----+
 *          | Q6 |  ZOMBIE   |<---------| Q3 | LOGGED_IN |              Q2  | -  | -  | N1 | -  | N7 |
 *          +----+-----------+          +----+-----------+             -----+----+----+----+----+----+
 *                         ^              |            ^                Q3  | -  | -  | -  |N5  | N3 |
 *                      N6 |           N5 |            | N4            -----+----+----+----+----+----+
 *                         |              v            |                Q4  | -  | -  | N4 | -  | N6 |
 *                         |            +----+-----------+             -----+----+----+----+----+----+
 *                         +------------| Q4 |   FAILED  |              Q6  | -  | -  | -  | -  | -  |
 *                                      +----+-----------+             -----+----+----+----+----+----+
 *
 *                          State Transition Diagram                       State Transition Matrix
 *
 *
 * Session State Descriptions
 *
 *    -Q1: FREE       State on instantiation.
 *
 *    -Q2: ACTIVE     State after session login has been initiated.
 *
 *    -Q3: LOGGED_IN  Waiting for all session events.
 *
 *    -Q4: FAILED     Waiting for session recovery or session continuation.
 *
 *    -Q6: ZOMBIE     State after session closure or reset.
 *
 *
 * Session Transition Descriptions
 *
 *    -N1:            At least one transport connection reached the LOGGED_IN state.
 *
 *    -N2:            Session login has been initiated.
 *
 *    -N3:            Graceful closing of the session via session closure or session reset due to irrecoverable
 *                    transport or session failures.
 *
 *    -N4:            A session continuation attempt succeeded.
 *
 *    -N5:            Session failure occurred whence the last operational transport has been closed due to abnormal
 *                    causes and all outstanding commands start to wait for recovery.
 *
 *    -N6:            Session state timeout occurred, or a session reinstatement cleared this session instance. This
 *                    results in the freeing of all associated resources and the session state is discarded.
 *
 *    -N7:            Session failure occurred before it reached the LOGGED_IN state due to session state timeout,
 *                    last transport withdrawn, or irrecoverable login failures from the server.
 */
public enum ClientSessionState {

    FREE,
    ACTIVE,
    LOGGED_IN,
    FAILED,
    ZOMBIE;

    private static Map<ClientSessionState, EnumSet<ClientSessionState>> fsm;

    static {
        fsm = new HashMap<ClientSessionState, EnumSet<ClientSessionState>>();

        // Session FSM definition
        fsm.put(null, EnumSet.of(FREE));
        fsm.put(FREE, EnumSet.of(ACTIVE));
        fsm.put(ACTIVE, EnumSet.of(LOGGED_IN, ZOMBIE));
        fsm.put(LOGGED_IN, EnumSet.of(FAILED, ZOMBIE));
        fsm.put(FAILED, EnumSet.of(LOGGED_IN, ZOMBIE));
        fsm.put(ZOMBIE, EnumSet.noneOf(ClientSessionState.class));
    }

    /**
     * Validate if the specified session state is reachable from the current state according to the formal definition
     * of the session finite state machine.
     */
    public static void validate(ClientSessionState oldState, ClientSessionState newState) {
        EnumSet<ClientSessionState> states = fsm.get(oldState);

        if (!states.contains(newState)) {
            throw new IllegalStateException("illegal transition from " + oldState + " to " + newState);
        }
    }
}
