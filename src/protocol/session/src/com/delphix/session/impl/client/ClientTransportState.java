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
 * Transport State Machine
 * =======================
 *
 *                          +--------------+
 *                          |              |
 *                          v              |
 *           +----+-----------+            |
 *     +---->| S1 |    FREE   |<----+      |
 *     |     +----+-----------+     |      |
 *     |    T1 |            ^ T2    |      |
 *     |       v            |       |      |
 *     |     +----+-----------+     |      |
 *     |     | S2 | XPRT_WAIT |     | T7   | T13
 *     |     +----+-----------+     |      |                     +----+---+---+---+---+
 *     |    T4 |                    |      |                     |S1  |S2 |S4 |S5 |S6 |
 *  T8 |       v                    |      |                  ---+----+---+---+---+---+
 *     |     +----+-----------+     |      |                   S1| -  |T1 | - | - | - |
 *     |     | S4 | IN_LOGIN  |-----+      |                  ---+----+---+---+---+---+
 *     |     +----+-----------+            |                   S2|T2  |-  |T4 | - | - |
 *     |    T5 |                           |                  ---+----+---+---+---+---+
 *     |       v                           |                   S4|T7  |-  |-  |T5 | - |
 *     |     +----+-----------+            |                  ---+----+---+---+---+---+
 *     +-----| S5 | LOGGED_IN |            |                   S5|T8  |-  |-  | - |T9 |
 *           +----+-----------+            |                  ---+----+---+---+---+---+
 *          T9 |                           |                   S6|T13 |-  |-  | - | - |
 *             v                           |                  ---+----+---+---+---+---+
 *           +----+-----------+            |
 *           | S6 | IN_LOGOUT |------------+
 *           +----+-----------+
 *
 *       State Transition Diagram                               State Transition Matrix
 *
 *
 * Transport State Descriptions
 *
 *    -S1: FREE       State on instantiation, or after connection closure.
 *
 *    -S2: XPT_WAIT   Waiting for a response to its transport connection establishment request.
 *
 *    -S4: IN_LOGIN   Waiting for the Login process to conclude, possibly involving several PDU exchanges.
 *
 *    -S5: LOGGED_IN  Waiting for all internal, protocol, and transport events.
 *
 *    -S6: IN_LOGOUT  Waiting for a Logout response.
 *
 *
 * State Transition Descriptions
 *
 *    -T1:            Transport connect request was made (e.g., TCP SYN sent).
 *
 *    -T2:            Transport connection request timed out or a transport reset was received.
 *
 *    -T4:            Transport connection established, thus prompting the client to start the Login.
 *
 *    -T5:            The final Login Response with a success status was received.
 *
 *    -T7:            One of the following events caused the transition:
 *
 *                      - The final Login Response was received with a failure status.
 *                      - Login timed out.
 *                      - A transport disconnect indication was received.
 *                      - A transport reset was received.
 *                      - An internal event was received indicating a transport timeout.
 *                      - An internal event of receiving a Logout response (success) on another connection for a
 *                       "close the session" Logout request was received.
 *
 *                    In all these cases, the transport connection is closed.
 *
 *    -T8:            An internal event of receiving a Logout response (success) on another connection for a "close
 *                    the session" Logout request was received, thus closing this connection requiring no further
 *                    cleanup.
 *
 *    -T9:            An internal event that indicates readiness to start the Logout process was received. A Logout
 *                    request was sent as a result.
 *
 *   -T13:            An Logout response (success) was received, or an internal event of receiving a Logout response
 *                    (success) on another connection for a "close the session" Logout request was received.
 */
public enum ClientTransportState {

    FREE,
    XPT_WAIT,
    IN_LOGIN,
    LOGGED_IN,
    IN_LOGOUT;

    private static Map<ClientTransportState, EnumSet<ClientTransportState>> fsm;

    static {
        fsm = new HashMap<ClientTransportState, EnumSet<ClientTransportState>>();

        // Transport FSM definition
        fsm.put(null, EnumSet.of(FREE));
        fsm.put(FREE, EnumSet.of(XPT_WAIT));
        fsm.put(XPT_WAIT, EnumSet.of(FREE, IN_LOGIN));
        fsm.put(IN_LOGIN, EnumSet.of(FREE, LOGGED_IN));
        fsm.put(LOGGED_IN, EnumSet.of(FREE, IN_LOGOUT));
        fsm.put(IN_LOGOUT, EnumSet.of(FREE));
    }

    /**
     * Validate if the specified session transport state is reachable from the current state according to the formal
     * definition of the session transport finite state machine.
     */
    public static void validate(ClientTransportState oldState, ClientTransportState newState) {
        EnumSet<ClientTransportState> states = fsm.get(oldState);

        if (!states.contains(newState)) {
            throw new IllegalStateException("illegal transition from " + oldState + " to " + newState);
        }
    }
}
