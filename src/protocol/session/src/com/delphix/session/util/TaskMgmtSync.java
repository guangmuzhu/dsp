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

/**
 * This class describes the task management serialization support. Task processing is race prone with respect to task
 * management. There may be various stages during task processing where serialization with task management is required.
 * This class defines a light-weight serialization mechanism using a state machine based approach that imposes minimal
 * overhead on task processing.
 */
public class TaskMgmtSync {

    private TaskMgmtState state;
    private int count;

    public TaskMgmtSync() {
        setState(TaskMgmtState.INACTIVE);
    }

    public TaskMgmtState getState() {
        return state;
    }

    private void setState(TaskMgmtState newState) {
        TaskMgmtState.validate(state, newState);
        state = newState;
    }

    /**
     * Block task management during command processing. It implements the serialization support required by task
     * management. The command processing logic is expected to invoke this method just before it enters a critical
     * section. If an abort has been requested on the command already, the caller must halt further processing at
     * the earliest convenience. This method is reentrant in that it may be invoked multiple times by the same
     * caller.
     */
    public synchronized boolean block() {
        boolean status = true;

        switch (state) {
        case INACTIVE:
            /*
             * A command is in the INACTIVE state if it has not been blocked by any active command processing context.
             * It will transition to the ACTIVE state with the active count increased as a result.
             */
            assert count == 0;
            count++;

            setState(TaskMgmtState.ACTIVE);

            break;

        case ACTIVE:
            /*
             * A command is in the ACTIVE state if it has been blocked. Task management block is reentrant, i.e., it
             * is OK to block it again. The result is accumulative with the command left in the ACTIVE state and the
             * active count increased.
             */

        case PENDING:
            /*
             * A command is set to the PENDING state if and only if abort is requested while it is in the ACTIVE
             * state. The command is treated similarly as in the ACTIVE state. This may starve the task management
             * context. But it is justified to do so since we should let the command continue if the processing has
             * not been completely stalled.
             */
            assert count > 0;
            count++;
            break;

        case ABORTING:
            /*
             * A command is in the ABORTING state if an abort has already been requested on the command. We will
             * leave the command in the ABORTING state and fail the block attempt.
             */
            assert count == 0;
            status = false;
            break;
        }

        return status;
    }

    /**
     * Unblock task management during command processing. It implements the serialization support required by task
     * management. The command processing logic is expected to invoke this method after it exits a critical section.
     */
    public synchronized void unblock() {
        switch (state) {
        case ACTIVE:
            /*
             * A command is in the ACTIVE state if there is no task management pending. The command will remain in
             * the state unless this is the last active processing context, in which case the command will transition
             * to the INACTIVE state.
             */
            assert count > 0;

            if (--count == 0) {
                setState(TaskMgmtState.INACTIVE);
            }

            break;

        case PENDING:
            /*
             * The command is set to the PENDING state from the task management context. It is processed here similar
             * to the ACTIVE state except that we need to notify the task management context.
             */
            assert count > 0;

            if (--count == 0) {
                setState(TaskMgmtState.INACTIVE);
                notify();
            }

            break;

        case INACTIVE:
            /*
             * A command should not be in the INACTIVE state since task management unblock must follow a successfully
             * processed task management block.
             */

        case ABORTING:
            /*
             * A command should not be in the INACTIVE state since task management is not allowed to proceeed while
             * the task management is blocked.
             */
            assert false;
        }
    }

    /**
     * Prepare the task before the abort. It implements the serialization support required by task management before
     * doing the actual task abort.
     */
    public synchronized void start() throws InterruptedException {
        switch (state) {
        case ACTIVE:
            /*
             * A command is in the ACTIVE state if it is being actively processed. We must hold off task management
             * to serialize access to critical data and at the same time set the state to PENDING to indicate the
             * intention to abort.
             */
            setState(TaskMgmtState.PENDING);

            // FALLTHRU
        case PENDING:
            /*
             * It should be rare to hit the wait given the typical timing for task management when compared against
             * that of command processing. In case we do hit the wait, it should be brief because command processing
             * is non-blocking.
             */
            while (state == TaskMgmtState.PENDING) {
                assert count > 0;

                /*
                 * In the rare event that we failed to wait (due to interrupt), leave the task state as PENDING.
                 * It will eventually be reset during task processing.
                 */
                wait();

                // Set the state back to PENDING and start over in case of a race with the active context
                if (state == TaskMgmtState.ACTIVE) {
                    setState(TaskMgmtState.PENDING);
                }
            }

            // FALLTHRU
        case INACTIVE:
            /*
             * A command is in the INACTIVE state if it is not being actively processed. Task management may commence
             * right away with the state set to ABORTING.
             */
            setState(TaskMgmtState.ABORTING);

            // FALLTHRU
        case ABORTING:
            /*
             * A command is in the ABORTING state if it remains outstanding following an earlier attempt to abort.
             * The command remains in the same state with the abort retried.
             */
            assert count == 0;
            break;
        }
    }

    /**
     * Check if task management has been blocked on the command.
     */
    public synchronized boolean isBlocked() {
        return state == TaskMgmtState.ACTIVE || state == TaskMgmtState.PENDING;
    }

    /**
     * Check if task management has started on the command.
     */
    public synchronized boolean isStarted() {
        return state == TaskMgmtState.ABORTING;
    }
}
