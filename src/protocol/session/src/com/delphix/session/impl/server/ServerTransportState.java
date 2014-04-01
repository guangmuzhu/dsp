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
 * Transport State Machine
 * =======================
 *
 *                           +--------------+
 *                           |              |
 *                           v              |
 *            +----+-----------+            |
 *      +---->| S1 |    Free   |<----+      |
 *      |     +----+-----------+     |      |
 *      |    T3 |            ^ T6    |      |
 *      |       v            |       |      |
 *      |     +----+-----------+     |      |
 *      |     | S3 |  XPRT_UP  |     | T7   | T13
 *      |     +----+-----------+     |      |
 *      |    T4 |                    |      |
 *   T8 |       v                    |      |                    +----+---+---+---+---+
 *      |     +----+-----------+     |      |                    |S1  |S3 |S4 |S5 |S6 |
 *      |     | S4 | IN_LOGIN  |-----+      |                 ---+----+---+---+---+---+
 *      |     +----+-----------+            |                  S1| -  |T3 | - | - | - |
 *      |    T5 |                           |                 ---+----+---+---+---+---+
 *      |       v                           |                  S3|T6  |-  |T4 | - | - |
 *      |     +----+-----------+            |                 ---+----+---+---+---+---+
 *      +-----| S5 | LOGGED_IN |            |                  S4|T7  |-  |-  |T5 | - |
 *            +----+-----------+            |                 ---+----+---+---+---+---+
 *           T9 |                           |                  S5|T8  |-  |-  | - |T9 |
 *              v                           |                 ---+----+---+---+---+---+
 *            +----+-----------+            |                  S6|T13 |-  |-  | - | - |
 *            | S6 | IN_LOGOUT |------------+                 ---+----+---+---+---+---+
 *            +----+-----------+
 *
 *        State Transition Diagram                             State Transition Matrix
 *
 *
 * Transport State Descriptions
 *
 *    -S1: FREE       State on instantiation, or after connection closure.
 *
 *    -S3: XPT_UP     Waiting for the Login process to commence.
 *
 *    -S4: IN_LOGIN   Waiting for the Login process to conclude, possibly involving several PDU exchanges.
 *
 *    -S5: LOGGED_IN  Waiting for all internal, protocol, and transport events.
 *
 *    -S6: IN_LOGOUT  Waiting for an internal event signaling completion of logout processing.
 *
 *
 * State Transition Descriptions
 *
 *    -T3:            Received a valid transport connection request that establishes the transport connection.
 *
 *    -T4:            Initial Login request was received.
 *
 *    -T5:            The final Login request to conclude the Login Phase was received, thus prompting the server
 *                    to send the final Login response with a success status.
 *
 *    -T6:            Timed out waiting for a Login, transport disconnect indication was received, transport reset
 *                    was received, or an internal event indicating a transport timeout was received. In all these
 *                    cases, the connection is to be closed.
 *
 *    -T7:            One of the following events caused the transition:
 *
 *                      - The final Login request to conclude the Login Phase was received, prompting the server to
 *                       send the final Login response with a failure status.
 *                      - Login timed out.
 *                      - Transport disconnect indication was received.
 *                      - Transport reset was received.
 *                      - An internal event indicating a transport timeout was received.
 *                      - On another connection a "close the session" Logout request was received.
 *
 *                    In all these cases, the connection is to be closed.
 *
 *    -T8:            An internal event of sending a Logout response (success) on another connection for a "close
 *                    the session" Logout request was received, or an internal event of a successful session
 *                    reinstatement is received, thus prompting the server to close this connection cleanly.
 *
 *    -T9:            A Logout request was received.
 *
 *   -T13:            An internal event was received that indicates successful processing of the Logout, which
 *                    prompts an Logout response (success) to be sent; an internal event of sending a Logout
 *                    response (success) on another connection for a "close the session" Logout request was
 *                    received; or an internal event of a successful session reinstatement is received. In all
 *                    these cases, the transport connection is closed.
 */
public enum ServerTransportState {

    FREE,
    XPT_UP,
    IN_LOGIN,
    LOGGED_IN,
    IN_LOGOUT;

    private static Map<ServerTransportState, EnumSet<ServerTransportState>> fsm;

    static {
        fsm = new HashMap<ServerTransportState, EnumSet<ServerTransportState>>();

        // Transport FSM definition
        fsm.put(null, EnumSet.of(FREE));
        fsm.put(FREE, EnumSet.of(XPT_UP));
        fsm.put(XPT_UP, EnumSet.of(FREE, IN_LOGIN));
        fsm.put(IN_LOGIN, EnumSet.of(FREE, LOGGED_IN));
        fsm.put(LOGGED_IN, EnumSet.of(FREE, IN_LOGOUT));
        fsm.put(IN_LOGOUT, EnumSet.of(FREE));
    }

    /**
     * Validate if the specified session transport state is reachable from the current state according to the formal
     * definition of the session transport finite state machine.
     */
    public static void validate(ServerTransportState oldState, ServerTransportState newState) {
        EnumSet<ServerTransportState> states = fsm.get(oldState);

        if (!states.contains(newState)) {
            throw new IllegalStateException("illegal transition from " + oldState + " to " + newState);
        }
    }
}
