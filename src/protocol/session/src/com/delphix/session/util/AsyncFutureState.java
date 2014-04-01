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

package com.delphix.session.util;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Async Future State Machine
 * ==========================
 *
 *                    N7         +----+----------+
 *            +----------------- | S3 | COMPLETED|<------+
 *            |                  +----+----------+       |
 *            |                          ^               |                    +----+----+----+----+----+
 *            |                          | N2            |                    | S1 | S2 | S3 | S4 | S5 |
 *            v                          |               |               -----+----+----+----+----+----+
 *    +----+---------+    N1     +----+---------+        |                S1  | -  | N1 | -  | N5 | -  |
 *    | S1 | INITIAL |---------->| S2 | ACTIVE  |        |               -----+----+----+----+----+----+
 *    +----+---------+           +----+---------+        |                S2  | -  | -  | N2 | -  | N3 |
 *           |                           |               |               -----+----+----+----+----+----+
 *        N5 |                           | N3            |                S3  | -  | -  | -  | -  | -  |
 *           v                           v               |               -----+----+----+----+----+----+
 *    +----+---------+    N4     +----+---------+   N6   |                S4  | -  | -  | -  | -  | -  |
 *    | S4 | ABORTED |<----------| S5 | ABORTING|--------+               -----+----+----+----+----+----+
 *    +----+---------+           +----+---------+                         S5  | -  | -  | N6 | N4 | -  |
 *                                                                       -----+----+----+----+----+----+
 *
 *              State Transition Diagram                                     State Transition Matrix
 *
 *
 * Future State Descriptions
 *
 *    -S1: INITIAL     Initial state of the future upon creation.
 *
 *    -S2: ACTIVE      The task associated with the future has been submitted for execution and is actively running.
 *
 *    -S3: COMPLETED   The task associated with the future has completed.
 *
 *    -S4: ABORTED     The task associated with the future has stopped running due to cancellation.
 *
 *    -S5: ABORTING    Cancellation has been requested on the task while it is active.
 *
 *
 * Future Transition Descriptions
 *
 *    -N1:             The task has been started via the run() method.
 *
 *    -N2:             The task has completed successfully.
 *
 *    -N3:             Cancellation has been requested on the task while it is active. The final state of the task
 *                     will be determined when the task returns from the run() method.
 *
 *    -N4:             The task has been interrupted while it was running due to cancellation request.
 *
 *    -N5:             The task has been cancelled before it is even started. The cancellation happens synchronously
 *                     in this case without waiting.
 *
 *    -N6:             The task has completed even though cancellation has been requested on it. Either the execution
 *                     context was not interrupted or the interrupt was ignored.
 *
 *    -N7:             The task has completed and moved to the initial state.
 *                     NOTE: This transition is only valid for recurring tasks.
 */
public enum AsyncFutureState {

    INITIAL,
    ACTIVE,
    ABORTING,
    ABORTED,
    COMPLETED;

    private static Map<AsyncFutureState, EnumSet<AsyncFutureState>> fsm;

    static {
        fsm = new HashMap<AsyncFutureState, EnumSet<AsyncFutureState>>();

        // Task Management FSM definition
        fsm.put(null, EnumSet.of(INITIAL));
        fsm.put(INITIAL, EnumSet.of(ACTIVE, ABORTED));
        fsm.put(ACTIVE, EnumSet.of(COMPLETED, ABORTING));
        fsm.put(ABORTING, EnumSet.of(ABORTED, COMPLETED));
        fsm.put(ABORTED, EnumSet.noneOf(AsyncFutureState.class));
        fsm.put(COMPLETED, EnumSet.of(INITIAL));
    }

    /**
     * Validate if the specified async future state is reachable from the current state according to the formal
     * definition of the async future finite state machine.
     */
    public static void validate(AsyncFutureState oldState, AsyncFutureState newState) {
        EnumSet<AsyncFutureState> states = fsm.get(oldState);

        if (!states.contains(newState)) {
            throw new IllegalStateException("invalid state transition from " + oldState + " to " + newState);
        }
    }
}
