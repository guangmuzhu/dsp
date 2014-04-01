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
 * Task Management State Machine
 * =============================
 *
 *           +----+----------+  N5  +----+----------+
 *           | S2 |  ACTIVE  |----->| S3 |  PENDING |
 *           +----+----------+      +----+----------+
 *              |         ^            |
 *           N2 |         | N1         | N4                              +----+----+----+----+
 *              v         |            |                                 | S1 | S2 | S3 | S4 |
 *           +----+----------+         |                            -----+----+----+----+----+
 *           | S1 | INACTIVE |<--------+                             S1  | -  | N1 | -  | N3 |
 *           +----+----------+                                      -----+----+----+----+----+
 *              |                                                    S2  | N2 | -  | N5 | -  |
 *              | N3                                                -----+----+----+----+----+
 *              v                                                    S3  | N4 | -  | -  | -  |
 *           +----+----------+                                      -----+----+----+----+----+
 *           | S4 | ABORTING |                                       S4  | -  | -  | -  | -  |
 *           +----+----------+                                      -----+----+----+----+----+
 *
 *        State Transition Diagram                                     State Transition Matrix
 *
 *
 * Task Management State Descriptions
 *
 *    -S1: INACTIVE   Task inactive and task management may proceed without delay.
 *
 *    -S2: ACTIVE     Task active and task management must be blocked.
 *
 *    -S3: PENDING    Task management is requested while task is active.
 *
 *    -S4: ABORTING   Task management has commenced and task is being aborted.
 *
 *
 * Task Management Transition Description
 *
 *    -N1:            Task has been activated to block task management.
 *
 *    -N2:            Task has been deactivated to unblock task management.
 *
 *    -N3:            Task management has commenced and task is being aborted.
 *
 *    -N4:            Task has been deactivated to unblock task management.
 *
 *    -N5:            Task management has been requested while the task is active.
 */
public enum TaskMgmtState {

    INACTIVE,
    ACTIVE,
    PENDING,
    ABORTING;

    private static Map<TaskMgmtState, EnumSet<TaskMgmtState>> fsm;

    static {
        fsm = new HashMap<TaskMgmtState, EnumSet<TaskMgmtState>>();

        // Task Management FSM definition
        fsm.put(null, EnumSet.of(INACTIVE));
        fsm.put(INACTIVE, EnumSet.of(ACTIVE, ABORTING));
        fsm.put(ACTIVE, EnumSet.of(INACTIVE, PENDING));
        fsm.put(PENDING, EnumSet.of(INACTIVE));
        fsm.put(ABORTING, EnumSet.noneOf(TaskMgmtState.class));
    }

    /**
     * Validate if the specified task management state is reachable from the current state according to the formal
     * definition of the task management finite state machine.
     */
    public static void validate(TaskMgmtState oldState, TaskMgmtState newState) {
        EnumSet<TaskMgmtState> states = fsm.get(oldState);

        if (!states.contains(newState)) {
            throw new IllegalStateException("invalid state transition from " + oldState + " to " + newState);
        }
    }
}
