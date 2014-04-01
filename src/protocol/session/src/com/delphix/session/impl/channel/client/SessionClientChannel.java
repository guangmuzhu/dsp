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
 * Copyright (c) 2013, 2014 by Delphix. All rights reserved.
 */

package com.delphix.session.impl.channel.client;

import com.delphix.platform.PlatformManagerLocator;
import com.delphix.session.impl.client.ClientLogoutFuture;
import com.delphix.session.impl.common.*;
import com.delphix.session.impl.frame.*;
import com.delphix.session.service.*;
import com.delphix.session.util.AsyncTask;
import com.delphix.session.util.TaskMgmtSync;

import java.util.*;

import static com.delphix.session.service.ServiceOption.SYNC_DISPATCH;
import static com.delphix.session.service.ServiceOption.XPORT_SCHEDULER;

public class SessionClientChannel extends SessionChannel {

    /*
     * Command set for all outstanding commands. This helps us keep track of all commands that have entered the
     * channel but not left yet for debugging purpose. It also ensures that nothing is left behind during channel
     * shutdown.
     */
    private final Set<SessionClientCommand> registry = new HashSet<SessionClientCommand>();

    // Command queue for those waiting to be started
    private final LinkedList<SessionClientCommand> pendingQueue = new LinkedList<SessionClientCommand>();

    // Command queue for those waiting to be retried
    private final Queue<SessionClientCommand> retryQueue = new LinkedList<SessionClientCommand>();

    // Command queue for those waiting to be aborted
    private final Queue<SessionClientCommand> abortQueue = new LinkedList<SessionClientCommand>();

    // Command set for those active or in flight (for tracking purpose only)
    private final Set<SessionClientCommand> activeSet = new HashSet<SessionClientCommand>();

    // Command set for those with active task management (for tracking purpose only)
    private final Set<SessionClientCommand> abortSet = new HashSet<SessionClientCommand>();

    /*
     * Command set for those aborted locally. It contains all the commands, over the lifetime of the channel, that
     * have been completed back up to the application without having the associated channel state synchronized. We
     * add all the commands from the abort queue to the stale set when the channel is disconnect and remove command
     * from the stale set when the command is completed (due to task management). The stale set must be completely
     * cleared before new commands are processed to avoid potential inconsistency.
     */
    private final Set<SessionClientCommand> staleSet = new HashSet<SessionClientCommand>();

    private final SessionClientTask retryTask; // Channel retry task
    private final SessionClientTask abortTask; // Channel abort task
    private final SessionClientTask restartTask; // Channel restart task

    private final SessionClientSlotTable slotTable; // Command slot table
    private final SessionTransportScheduler scheduler; // Transport scheduler
    private final SessionThrottler throttler; // Throughput throttler

    private SessionClientLogout logout; // Session logout exchange
    private final TaskMgmtSync shutdown; // Channel shutdown sync

    private final SessionClientChannelStats stats; // Channel stats

    private final boolean syncDispatch; // Sync dispatch

    public SessionClientChannel(SessionNexus nexus, boolean fore, int queueDepth) {
        this(nexus, fore, queueDepth, null, 0);
    }

    public SessionClientChannel(SessionNexus nexus, boolean fore, int queueDepth, SerialNumber commandSN) {
        this(nexus, fore, queueDepth, commandSN, 0);
    }

    public SessionClientChannel(SessionNexus nexus, boolean fore, int queueDepth, int bandwidthLimit) {
        this(nexus, fore, queueDepth, null, bandwidthLimit);
    }

    public SessionClientChannel(SessionNexus nexus, boolean fore, int queueDepth, SerialNumber commandSN,
            int bandwidthLimit) {
        super(nexus, fore);

        // Initialize the command sequence
        if (commandSN == null) {
            commandSN = SerialNumber.ZERO_SEQUENCE_INTEGER;
        }

        this.commandSN = commandSN;

        // Create the transport scheduler
        String option = nexus.getOptions().getOption(XPORT_SCHEDULER);
        scheduler = ScheduleMethod.valueOf(option).create();

        // Create the session throttler
        throttler = new SessionThrottler(bandwidthLimit * (1024 * 1024), this);

        slotTable = new SessionClientSlotTable(queueDepth);

        stats = new SessionClientChannelStats(this);

        shutdown = new TaskMgmtSync();

        // Create the channel retry task
        retryTask = new SessionClientTask(retryQueue) {

            @Override
            protected void doWork(SessionClientCommand command) {
                retryCommand(command);
            }
        };

        // Create the channel abort task
        abortTask = new SessionClientTask(abortQueue) {

            @Override
            protected void doWork(SessionClientCommand command) {
                abortCommand(command);
            }
        };

        // Create the channel restart task
        restartTask = new SessionClientTask(pendingQueue) {

            @Override
            public boolean isReady() {
                return super.isReady() && (throttler.needTokens() || slotTable.available()) && staleSet.isEmpty();
            }

            @Override
            protected void doWork(SessionClientCommand command) {
                restartCommand(command);
            }
        };

        // Set the dispatch mode
        syncDispatch = nexus.getOptions().getOption(SYNC_DISPATCH);
    }

    public static void initialize(RequestFrame request) {
        request.setCommandSN(SerialNumber.ZERO_SEQUENCE_INTEGER);
        request.setMaxSlotIDInUse(0);
    }

    @Override
    public Map<String, ?> getStats() {
        return stats.getStats();
    }

    @Override
    public void resetStats() {
        stats.resetStats();
    }

    @Override
    public boolean isClient() {
        return true;
    }

    @Override
    public boolean isConnected() {
        return !scheduler.isEmpty();
    }

    @Override
    public SerialNumber getCommandSN() {
        return commandSN;
    }

    public SerialNumber nextCommandSN() {
        SerialNumber current = commandSN;
        commandSN = current.next();
        return current;
    }

    @Override
    public void setExpectedCommandSN(SerialNumber commandSN) {
        if (this.commandSN.lessThan(commandSN)) {
            throw new ProtocolViolationException("invalid expected command sequence " + commandSN);
        }

        if (expectedCommandSN == null || expectedCommandSN.lessThan(commandSN)) {
            expectedCommandSN = commandSN;
        }
    }

    @Override
    public synchronized void refresh(RequestFrame request) {
        SerialNumber expectedCommandSN = request.getExpectedCommandSN();

        if (expectedCommandSN != null) {
            setExpectedCommandSN(expectedCommandSN);
        }
    }

    @Override
    public synchronized void refresh(ResponseFrame response) {
        setExpectedCommandSN(response.getExpectedCommandSN());

        slotTable.update(response.getCurrentMaxSlotID(), response.getTargetMaxSlotID());
    }

    @Override
    public synchronized void update(RequestFrame request) {
        /*
         * Command request has a command SN assigned to it upon entry to the session (see commandStart). The command
         * SN remains the same for the life of that command. Non-command requests carry the latest command SN so it
         * gets updated every time it is sent or resent.
         */
        if (!(request instanceof CommandRequest)) {
            request.setCommandSN(getCommandSN());
        }

        request.setExpectedCommandSN(getSibling().getExpectedCommandSN());

        request.setMaxSlotIDInUse(slotTable.getMaxSlotIDInUse());
    }

    @Override
    public synchronized void update(ResponseFrame response) {
        response.setCommandSN(getCommandSN());
    }

    public int getActiveCommands() {
        return activeSet.size();
    }

    public int getRetryCommands() {
        return retryQueue.size();
    }

    public int getPendingCommands() {
        return pendingQueue.size();
    }

    public int getTotalCommands() {
        return registry.size();
    }

    public int getAbortCommands() {
        return abortQueue.size() + abortSet.size();
    }

    public ServiceFuture execute(ServiceRequest request, Runnable done, long timeout) {
        SessionClientCommand command = new SessionClientCommand(this, request, done);
        ServiceFuture future = command.getFuture();

        // Schedule command timeout if necessary
        command.scheduleTimeout(timeout);

        // Future run will end up calling dispatch in the same context
        future.run();

        return future;
    }

    public void dispatch(SessionClientCommand command) {
        boolean interrupted = false;

        /*
         * Start the command processing. In the unlikely case where task management has already been requested on
         * the command, abort immediately.
         */
        if (!command.start()) {
            logger.debugf("%s: command aborted immediately while pending", command);
            commandAborted(command, "command aborted while pending");
            return;
        }

        /*
         * Try to start channel command processing. If the command could not be started immediately for various
         * reasons, it would have been queued in the channel for later processing. In that case, re-enable task
         * management before return.
         */
        if (!commandStart(command)) {
            logger.tracef("%s: command queued for restart later", command);
            command.enableTaskMgmt();

            // Return immediately if not in sync dispatch mode
            if (!syncDispatch) {
                return;
            }

            for (;;) {
                try {
                    /*
                     * The dispatch context will block here while the command is waiting in the pending queue. When
                     * a slot becomes available, the command will be activated from the restart task and the dispatch
                     * context waken up.
                     */
                    if (command.syncDispatch()) {
                        break;
                    }

                    /*
                     * In the case of an asynchronous channel shutdown, the command will be aborted while pending.
                     * When that happens, the dispatch context should return without sending.
                     */
                    logger.infof("%s: dispatch aborted due to channel shutdown", command);

                    return;
                } catch (InterruptedException e) {
                    logger.infof("%s: dispatch interrupted", command);

                    // Attempt to abort the command
                    if (commandAbortDispatch(command)) {
                        throw new DispatchInterruptedException();
                    }

                    /*
                     * In the rare case the command is being activated from the restart task, the activation cannot
                     * be undone and we must carry on with the send. We will return the future to the caller as if
                     * it was a normal dispatch.
                     */
                    logger.infof("%s: continue dispatch after interrupt", command);

                    /*
                     * The dispatch context was interrupted which signaled the intention to stop at the earliest
                     * convenience. Rather than swallowing the interrupt we must restore it at the end for deferred
                     * processing.
                     */
                    interrupted = true;
                }
            }
        }

        if (!command.send()) {
            commandQueueRetry(command);
        }

        // Re-enable task management
        command.enableTaskMgmt();

        // Restore interrupt if necessary
        if (interrupted) {
            PlatformManagerLocator.getInterruptStrategy().interrupt(Thread.currentThread());
        }
    }

    /**
     * Abort the given command from the dispatch context and return true if the command has not been activated.
     */
    private boolean commandAbortDispatch(SessionClientCommand command) {
        boolean aborted = true;

        // Shutdown may have already started in which case it will take care of the command
        if (!shutdown.block()) {
            logger.infof("%s: dispatch interrupted after shutdown initiated", command);
            return aborted;
        }

        /*
         * The dispatch context could be interrupted while waiting. The command must be aborted from the session
         * in that case before an DispatchInterruptedException is thrown.
         */
        aborted = commandAbortPending(command);

        if (aborted) {
            logger.infof("%s: dispatch interrupted - command aborted", command);
            commandAborted(command, "command aborted during dispatch");
        }

        shutdown.unblock();

        return aborted;
    }

    /**
     * State transitions: T1, T3
     *
     * Commands that cannot be started immediately are kept in the pending queue in the order of their arrival. A
     * pending command has not modified the vital channel state. Specifically, it has not been assigned a command
     * slot nor a command sequence. Hence, to abort a pending command does not require state synchronization with
     * the server. In the meantime, the pending queue ensures that the commands are always processed in order when
     * they are restarted later. There are a few conditions that prevent immediate start of a command.
     *
     *   - non-empty pending queue (i.e., restart task is running)
     *   - non-empty stale set (i.e. commands aborted locally)
     *   - channel not connected
     *   - command slot table full
     *
     * If none of the above conditions are met, the command shall be started immediately following which the channel
     * state must be kept in sync with the server regardless of the outcome of the command.
     */
    private synchronized boolean commandStart(SessionClientCommand command) {
        // Update the throttler's token count
        throttler.updateTokens();

        // Register the command and check if it can be started immediately or not
        if (!register(command)) {
            command.setState(SessionClientCommandState.PENDING);
            pendingQueue.offer(command);

            // Submit the restart task in case we failed to register the command due to throughput throttling.
            restartTask.submit();
            return false;
        }

        // Allocate a new commandSN for the command
        command.setCommandSN(nextCommandSN());

        // Schedule the transport to send the command over
        SessionTransport xport = scheduler.schedule(command);
        command.setTransport(xport);

        // The success of command registration above ensures that a transport exists in the scheduler
        assert xport != null;

        // Activate the command
        command.setState(SessionClientCommandState.ACTIVE);
        activeSet.add(command);

        return true;
    }

    /**
     * Register the command with the channel and confirm the conditions required for the immediate start of the
     * command as outlined in commandStart() above. Return true if the conditions are confirmed and false otherwise.
     */
    private boolean register(SessionClientCommand command) {
        // Check if the channel is connected
        if (!isConnected()) {
            logger.debugf("%s: command queued due to disconnected channel", command);

            /*
             * We must serialize with the shutdown context before putting anything into the registry when the channel
             * is disconnected. We don't need to worry about it when the channel is restored since shutdown is not
             * started until all transports have been detached.
             */
            if (!shutdown.block()) {
                // Stop accepting new commands if shutdown has already started
                throw new NexusResetException("failed to dispatch new command");
            }

            registry.add(command);
            shutdown.unblock();

            return false;
        }

        // Register the command with the channel
        registry.add(command);

        // Check if the restart task is done
        if (!restartTask.isDone()) {
            return false;
        }

        // Check if the stale set is empty
        if (!staleSet.isEmpty()) {
            return false;
        }

        // Consume the required tokens
        if (!throttler.consumeTokens(command, getCompressionRatio())) {
            return false;
        }

        /*
         * Reserve a slot on behalf of the command. Once a slot is reserved, the command is considered to have been
         * successfully started and the channel state must be kept in sync with the server regardless of the outcome
         * of the command.
         */
        SessionClientSlot slot = slotTable.reserve(command);

        if (slot == null) {
            return false;
        }

        return true;
    }

    /**
     * State transitions: T5
     *
     * The command is queued for retry after it has failed due to transport reset.
     */
    private synchronized void commandQueueRetry(SessionClientCommand command) {
        // Deactivate the command
        command.setState(SessionClientCommandState.RETRY);
        boolean removed = activeSet.remove(command);
        assert removed;

        command.resetTransport();

        // Submit the retry task
        retryTask.submit(command);
    }

    private void retryCommand(SessionClientCommand command) {
        /*
         * Disable task management first before attempting command retry. In the unlikely case where task management
         * has already been requested on the command, initiate the abort process here since we have already removed
         * it from the retry queue.
         */
        if (!command.disableTaskMgmt()) {
            logger.debugf("%s: command abort while waiting for retry", command);
            abortRequest(command);
            return;
        }

        /*
         * Attempt to retry the command. If the command could not be retried for various reasons, it would have been
         * put back on the retry queue for later processing. In that case, re-enable task management before return.
         */
        if (!commandRetry(command)) {
            command.enableTaskMgmt();
            return;
        }

        if (!command.send()) {
            commandQueueRetry(command);
        }

        // Re-enable task management
        command.enableTaskMgmt();
    }

    /**
     * Schedule a transport for the given command. The caller may block briefly waiting for a dead transport to be
     * detached from the channel. As a result, this cannot be called from the network context or deadlock would
     * ensue. Nor is this called from the dispatch path to keep it non-blocking. It is only used by the retry and
     * abort asynchronous tasks to avoid flooding an already dead transport.
     */
    private SessionTransport schedule(SessionClientCommand command) {
        SessionTransport xport;

        for (;;) {
            if (command != null) {
                xport = scheduler.schedule(command);
            } else {
                xport = scheduler.schedule();
            }

            if (xport == null || xport.isConnected()) {
                return xport;
            }

            /*
             * Transport disconnect is processed as a session event in a separate context. We need to pause a little
             * while here to allow the dead transport a chance to detach itself to avoid hitting on it again the next
             * time around.
             */
            while (scheduler.contains(xport)) {
                try {
                    wait(); // see detach() for notifyAll
                } catch (InterruptedException e) {
                    // Do nothing
                }
            }
        }
    }

    /**
     * State transitions: T9
     *
     * The command shall be reactivated unless the channel has been disconnected.
     */
    private synchronized boolean commandRetry(SessionClientCommand command) {
        // Schedule the transport to send the command over
        SessionTransport xport = schedule(command);

        if (xport == null) {
            retryQueue.offer(command);
            return false;
        }

        command.setTransport(xport);

        // Activate the command
        command.setState(SessionClientCommandState.ACTIVE);
        activeSet.add(command);

        command.resetStatus();

        return true;
    }

    private void restartCommand(SessionClientCommand command) {
        /*
         * Restart the command processing. In the unlikely case where task management has already been requested on
         * the command, abort immediately.
         */
        if (!command.start()) {
            logger.debugf("%s: command aborted immediately while pending", command);
            commandAborted(command, "command aborted while pending");
            return;
        }

        /*
         * Attempt to restart the command. If the command could not be retried for various reasons, it would have
         * been put back on the pending queue for later processing. In that case, re-enable task management before
         * return.
         */
        if (!commandRestart(command)) {
            command.enableTaskMgmt();
            return;
        }

        /*
         * In sync dispatch mode, use the dispatch context for send after the critical session level processing.
         * Doing so allows us to parallelize the potentially expensive send for higher scalability. Task management
         * remains disabled on the command until the dispatch context is done with it.
         */
        if (syncDispatch) {
            return;
        }

        if (!command.send()) {
            commandQueueRetry(command);
        }

        // Re-enable task management
        command.enableTaskMgmt();
    }

    /**
     * State transitions: T4
     *
     * The command shall be restarted unless the channel has been disconnected. A slot is guaranteed to be available
     * for the successful restart of the this command.
     */
    private synchronized boolean commandRestart(SessionClientCommand command) {
        // Consume the required tokens
        throttler.consumeTokens(command, getCompressionRatio(), true);

        // Check if the channel is connected
        if (!isConnected()) {
            pendingQueue.offerFirst(command);
            return false;
        }

        // Schedule the transport to send the command over
        SessionTransport xport = scheduler.schedule(command);
        command.setTransport(xport);

        /*
         * Reserve a slot for the command. Slot reservation may fail since the task may have been restarted due to
         * tokens becoming available.
         */
        if (slotTable.reserve(command) == null) {
            pendingQueue.offerFirst(command);
            return false;
        }

        assert command.getSlot() != null;

        // Allocate a command SN for the command
        command.setCommandSN(nextCommandSN());

        // Add the command to the active set before activating the command
        activeSet.add(command);

        // Activate the command and dispatch may start right away from the dispatch context
        command.setState(SessionClientCommandState.ACTIVE);

        return true;
    }

    /**
     * State transitions: T7, T12
     *
     * Request task management on behalf of the command. The command is queued while waiting for task management to
     * be initiated. The command should have been quiesced by now.
     */
    private synchronized boolean commandQueueAbort(SessionClientCommand command) {
        // Deactivate the abort if any
        if (command.getState() == SessionClientCommandState.ABORT) {
            boolean removed = abortSet.remove(command);
            assert removed;
            command.resetAbort();
        }

        command.setState(SessionClientCommandState.INDOUBT);

        // Submit the abort task
        abortTask.submit(command);

        // Abort early if channel is disconnected
        if (!isConnected()) {
            staleSet.add(command);
            return false;
        }

        return true;
    }

    /**
     * Request task management on behalf of the command. In case the channel is disconnected, initiate early command
     * completion.
     */
    private void abortRequest(SessionClientCommand command) {
        if (commandQueueAbort(command)) {
            return;
        }

        commandReset(command);
    }

    private void abortCommand(SessionClientCommand command) {
        SessionClientAbort abort;

        /*
         * Attempt to abort the command. If the command could not be aborted for various reasons, it would have been
         * put back on the abort queue for later processing.
         */
        if (!commandAbort(command)) {
            return;
        }

        abort = command.getAbort();

        if (!abort.send()) {
            abortRequest(command);
        }
    }

    /**
     * State transitions: T11
     *
     * Initiate a task management on behalf of the command to abort it from the server and synchronize the channel
     * state too. The command should have been quiesced by now.
     */
    private synchronized boolean commandAbort(SessionClientCommand command) {
        // Schedule the transport to send the abort over
        SessionTransport xport = schedule(null);

        if (xport == null) {
            abortQueue.offer(command);
            return false;
        }

        SessionClientAbort abort = new SessionClientAbort(this, command);
        abort.setTransport(xport);

        // Activate the abort
        command.setState(SessionClientCommandState.ABORT);
        abortSet.add(command);

        return true;
    }

    /**
     * State transitions: T2 T8, T10
     *
     * Complete the command from the channel. It is possible that a task management initiated on behalf of this
     * command may still be outstanding. When the task management completes, it will check whether the command has
     * completed from the channel before anything else.
     *
     * For stale command, we have already notified the application when the channel got disconnected. We only need
     * to complete it internally here without further notifying the application again.
     */
    private synchronized boolean commandComplete(SessionClientCommand command) {
        SessionClientCommandState state = command.getState();
        boolean notify = true;

        // Update the throttler's token count
        throttler.updateTokens();

        // Command state specific processing
        boolean removed;

        switch (state) {
        case ACTIVE:
            removed = activeSet.remove(command);
            assert removed;
            break;

        case PENDING:
            break;

        case ABORT:
            logger.infof("%s: command completed after abort", command);

            removed = abortSet.remove(command);
            assert removed;

            // Resume the restart task after the stale set is cleared
            if (staleSet.remove(command)) {
                notify = false;

                if (staleSet.isEmpty()) {
                    logger.infof("%s: restart channel after stale commands cleared", command);
                    restartTask.submit();
                }
            }

            break;

        default:
            throw new IllegalStateException("invalid command state " + state);
        }

        command.setState(SessionClientCommandState.FINAL);

        // Unregister the command
        removed = registry.remove(command);
        assert removed;

        // Release the command slot if any
        SessionClientSlot slot = command.getSlot();

        // Release the slot and resume the restart task
        if (slot != null) {
            slotTable.release(slot);
            restartTask.submit();
        }

        return notify;
    }

    /**
     * Complete the command with optionally a service exception generated by the protocol layer either locally or
     * over the wire.
     */
    private void completeCommand(SessionClientCommand command, Throwable t) {
        if (!commandComplete(command)) {
            return;
        }

        command.complete(t);

        // Update the channel stats
        stats.update(command);
    }

    /**
     * Command has been aborted before it started or completed. Complete the command with a service exception that
     * wraps around an interrupted exception. This will cause the service future to be set to cancelled.
     */
    private void commandAborted(SessionClientCommand command, String message) {
        completeCommand(command, new InterruptedException(message));
    }

    /**
     * Command has been aborted while the channel is disconnected. We let the command to be completed early without
     * waiting for the channel to be restored as that might take an indefinite amount of time. While this unblocks
     * the application early, the trade off is we will not know the command status until the channel is restored.
     * If the command is found to be unabortable, we could reset the session if so desired.
     */
    private void commandReset(SessionClientCommand command) {
        logger.infof("%s: command aborted while channel is down", command);

        /*
         * Mark the command state FINAL if the channel is being shutdown. In this case, we don't need to keep the
         * command state in sync any more. It would unblock a synchronous dispatch context for a command still in
         * PENDING state if necessary. We would never reset a PENDING command if the channel is not being shutdown.
         */
        if (!shutdown.isStarted()) {
            command.complete(new InterruptedException("command aborted while channel is down"));
        } else {
            command.setState(SessionClientCommandState.FINAL);
            command.complete(new NexusResetException("command failed"));
        }

        // Update the channel stats
        stats.update(command);
    }

    /**
     * Transport completion callback for the command.
     */
    public void exchangeDone(SessionClientCommand command) {
        // Disable task management before completion processing
        boolean disabled = command.disableTaskMgmt();

        /*
         * Command has failed with transport reset. Arrange command recovery if it is not being aborted. Otherwise,
         * let the task management take its course.
         */
        if (command.getStatus() != SessionTransportStatus.SUCCESS) {
            if (disabled) {
                logger.errorf("%s: command queued for retry", command);

                commandQueueRetry(command);
                command.enableTaskMgmt();
            }

            return;
        }

        // Process protocol response
        CommandResponse response = (CommandResponse) command.getResponse();
        CommandStatus status = response.getStatus();

        SessionClientSlot slot = command.getSlot();
        ServiceException exception = null;

        switch (status) {
        case SUCCESS:
            // Confirm since the slot has been advanced on the server
            slot.confirm();
            break;

        case SLOT_UNCACHED:
            // Confirm since the slot has been advanced on the server
            slot.confirm();

            /*
             * For an idempotent command the server makes no guarantee that the response will be cached until its
             * receipt has been acknowledged. Such a command may fail with the following exception when retried.
             * An application should catch this exception and retry the command if necessary.
             */
            exception = new IdempotentRetryException(status.getDesc());

            break;

        case SLOT_ID_INVALID:
        case SLOT_MAX_INVALID:
        case SLOT_SEQ_MISORDERED:
        case SLOT_FALSE_RETRY:
            // Rollback since the slot has not been advanced on the server
            slot.rollback();

            /*
             * These slot related errors are unexpected and may indicate severe problems such as session corruption
             * somewhere along the way. It is unlikely these errors are self healing so session reset might be the
             * remedy. We will leave that decision to the application.
             */
            exception = new CommandFailedException(status.getDesc());

            break;
        }

        if (exception != null) {
            logger.errorf("%s: command completed with %s", command, status);
        }

        /*
         * In case the command is still being aborted, we will just complete it here and let the task management
         * discover the command status when it completes later, as guaranteed by the transport completion ordering.
         */
        completeCommand(command, exception);

        if (disabled) {
            // Re-enable task management if successfully disabled earlier
            command.enableTaskMgmt();
        }
    }

    /**
     * Transport completion callback for the abort. Transport completion ordering guarantees that the command, if
     * was active on the same transport while the task management was initiated, should have been completed by now.
     */
    public void exchangeDone(SessionClientAbort abort) {
        SessionClientCommand command = abort.getTargetCommand();

        /*
         * Bail out if the command has already completed. The task management may be completed successfully or it
         * may have failed due to transport reset. In the former case, the task management response status must be
         * ALREADY_COMPLETED.
         */
        if (command.getStatus() == SessionTransportStatus.SUCCESS) {
            logger.infof("%s: target command already completed", abort);
            return;
        }

        // Retry the abort if it failed with transport reset - the command must have been quiesced by now
        if (abort.getStatus() != SessionTransportStatus.SUCCESS) {
            abortRequest(command);
            return;
        }

        // Process the task management response
        TaskMgmtResponse response = (TaskMgmtResponse) abort.getResponse();
        TaskMgmtStatus status = response.getStatus();
        SessionClientSlot slot = command.getSlot();

        logger.infof("%s: task management completed with %s", abort, status);

        switch (status) {
        case ABORTED_BEFORE_ARRIVAL:
        case ABORTED_BEFORE_START:
            // Confirm since the slot has been advanced on the server
            slot.confirm();

            /*
             * The application will learn of the abort or cancellation when it attempts to retrieve the result from
             * the future. The result is conveyed in the form of a cancellation exception, which does not have any
             * details on execution status.
             */
            commandAborted(command, status.getDesc());

            break;

        case ABORTED_AFTER_START:
        case ALREADY_COMPLETED:
            // Confirm since the slot has been advanced on the server
            slot.confirm();

            /*
             * We could complete the command with an exception to differentiate from the aborted before start cases
             * above if it is desired.
             */
            commandAborted(command, status.getDesc());

            break;

        case ABORTED_SLOT_FAILURE:
            // Rollback since the slot hasn't been advanced on the server
            slot.rollback();

            /*
             * The command won't be executed on the server if it has a slot failure. We will treat it the same as the
             * aborted before start cases. If the slot failure persists, it will get picked up on command completion
             * and the application will have a chance to deal with it.
             */
            commandAborted(command, status.getDesc());

            break;

        case UNABORTABLE:
            // Confirm since the slot has been advanced on the server
            slot.confirm();

            /*
             * In the current implementation, we will not see UNABORTABLE as a task management status. Instead, we
             * wait forever on the server side for a command to complete after abort is requested. This may tie up
             * a command slot for good. We could consider having a timeout on the server side. If the command does
             * not complete on its own before the timeout is reached we would then return UNABORTABLE and orphan the
             * command from the slot by force. When such status is communicated back to the client, the client may
             * decide whether it is still safe to continue session operation.
             */
            completeCommand(command, new AbortFailedException(status.getDesc()));

            break;
        }
    }

    /**
     * This is the main task management entry point. It is used to abort an outstanding command from the channel
     * whether due to timeout or other reasons. In the timeout case, it is invoked internally from the scheduled
     * executor service. Otherwise, it is invoked from one of the service future cancellation interfaces explicitly
     * by the issuer. This interface may block briefly (but not indefinitely) to accommodate command processing.
     */
    public void abort(SessionClientCommand command) {
        if (!shutdown.block()) {
            /*
             * Channel is already shutting down. If the command hasn't completed by now, it will be momentarily as
             * shutdown will force all lingering commands to complete.
             */
            return;
        }

        doAbort(command);

        // Unblock shutdown
        shutdown.unblock();
    }

    private void doAbort(SessionClientCommand command) {
        // Start task management on the command
        command.startTaskMgmt();

        /*
         * It is possible that abort may already be in progress for the command because of an earlier task management
         * attempt that didn't result in prompt command completion. Also, command processing may beat us in the race
         * after task management has been started. In either case, it is safe to continue.
         */

        /*
         * It is possible to race with the restart task. Whoever has succeeded in removing the command from the
         * restart queue wins the race and is responsible for completing the command.
         */
        if (commandAbortPending(command)) {
            logger.debugf("%s: command aborted immediately while pending", command);
            commandAborted(command, "command aborted while pending");
            return;
        }

        /*
         * It is possible to race with the retry task. Whoever has succeeded in removing the command from the
         * retry queue wins the race and is responsible for initiating the abort.
         */
        if (commandAbortRetry(command)) {
            logger.debugf("%s: command abort while waiting for retry", command);
            abortRequest(command);
            return;
        }

        /*
         * It is possible to race with command completion from the transport. Since the command is still active,
         * we have won the race and therefore should initiate the abort.
         */
        if (commandAbortActive(command)) {
            SessionClientAbort abort = command.getAbort();

            logger.debugf("%s: command abort while active", command);

            if (!abort.send()) {
                // Bail out if the command has already completed
                if (command.getStatus() == SessionTransportStatus.SUCCESS) {
                    logger.debugf("%s: target command already completed", abort);
                    return;
                }

                /*
                 * We rely on the transport to ensure that all outstanding exchanges are completed before an issue
                 * path exception is thrown. That eliminates the race of command completion after being queued for
                 * abort which would otherwise be possible.
                 */
                abortRequest(command);
            }

            return;
        }

        logger.debugf("%s: command already completed", command);
    }

    /**
     * Attempt to abort a pending command. If found, it will be removed from the pending queue to prevent a race
     * with the restart task. We will have exclusive access to the command following that.
     */
    private synchronized boolean commandAbortPending(SessionClientCommand command) {
        return pendingQueue.remove(command);
    }

    /**
     * Attempt to abort a command being retried. If found, it will be removed from the retry queue to prevent a race
     * with the retry task. We will have exclusive access to the command following that.
     */
    private synchronized boolean commandAbortRetry(SessionClientCommand command) {
        return retryQueue.remove(command);
    }

    /**
     * State transitions: T6
     *
     * Attempt to abort an in flight command. If found, a task management is initiated immediately while the command
     * response may still arrive. We do _not_ have exclusive access to the command until it is quiesced, meaning
     * completed back from the associated transport.
     */
    private synchronized boolean commandAbortActive(SessionClientCommand command) {
        if (!activeSet.contains(command)) {
            return false;
        }

        SessionClientAbort abort = new SessionClientAbort(this, command);

        /*
         * Schedule the transport to send the abort over. If the command is still active, send the abort over the
         * same transport the command is associated with. Also set up the dependency between the command and the
         * abort to ensure completion order is observed by the transport.
         */
        SessionTransport xport = command.getTransport();
        abort.setTransport(xport);

        // Activate the abort
        command.setState(SessionClientCommandState.ABORT);

        boolean removed = activeSet.remove(command);
        assert removed;
        abortSet.add(command);

        return true;
    }

    /**
     * Initiate a logout exchange with the server on behalf of the session. The session ensures this is done no more
     * than once over its lifetime. Currently, we do not make any extra effort to logout the session if we have lost
     * all the transports. We rely on the session timeout on the server or the session reinstatement mechanism to
     * eventually clean things up. We could have waited for connectivity to restore but that will delay closing.
     */
    public void logout(ClientLogoutFuture future) {
        assert logout == null : "logout already active";
        logout = new SessionClientLogout(this, future);

        sendLogout();
    }

    private void logoutQueueRetry() {
        logger.errorf("%s: logout queued for retry", nexus);

        nexus.execute(new Runnable() {

            @Override
            public void run() {
                sendLogout();
            }
        });
    }

    private void sendLogout() {
        logger.infof("%s: send logout request", nexus);

        synchronized (this) {
            // Schedule the transport to send the logout over
            SessionTransport xport = schedule(null);

            if (xport == null) {
                logout.complete(new LogoutFailedException("all transports lost"));
                return;
            }

            logout.setTransport(xport);
        }

        if (!logout.send()) {
            logoutQueueRetry();
        }
    }

    /**
     * Transport completion callback for session logout.
     */
    public void exchangeDone(SessionClientLogout logout) {
        // Logout failed with transport reset
        if (logout.getStatus() != SessionTransportStatus.SUCCESS) {
            logoutQueueRetry();
            return;
        }

        // Process protocol response
        LogoutResponse response = (LogoutResponse) logout.getResponse();
        LogoutStatus status = response.getStatus();
        SessionException e = null;

        logger.infof("%s: logout completed with %s", logout, status);

        switch (status) {
        case SUCCESS:
            break;

        case LOGOUT_FAILED:
            e = new LogoutFailedException("logout failed on server " + status);
            break;
        }

        // Complete the logout
        logout.complete(e);
    }

    /**
     * Attach the transport from the channel. Channel connectivity management, including transport attachment and
     * detachment, is processed as a series of session events in chronological order.
     */
    @Override
    protected void attach(SessionTransport xport) {
        SessionClientPing ping = new SessionClientPing(this);

        ping.setTransport(xport);

        try {
            ping.send();
        } catch (TransportResetException e) {
            logger.errorf("%s: %s failed to ping server", nexus, xport);
        }

        synchronized (this) {
            logger.infof("%s: %s attached to channel", nexus, xport);

            boolean disconnected = scheduler.isEmpty();

            scheduler.attach(xport);

            if (disconnected) {
                // Resume the asynchronous channel tasks
                abortTask.submit();
                retryTask.submit();
                restartTask.submit();

                // Block shutdown which should not have started yet
                boolean status = shutdown.block();
                assert status;

                logger.infof("%s: channel restored", nexus);
            }
        }
    }

    /**
     * Detach the transport from the channel. Channel connectivity management, including transport attachment and
     * detachment, is processed as a series of session events in chronological order.
     */
    @Override
    protected void detach(SessionTransport xport) {
        logger.infof("%s: %s detached from the channel", nexus, xport);

        synchronized (this) {
            scheduler.detach(xport);

            // Notify async tasks waiting for transport detach - see schedule() for wait
            notifyAll();

            if (!scheduler.isEmpty()) {
                return;
            }
        }

        // Quiesce the asynchronous channel tasks
        retryTask.finish();
        abortTask.finish();
        restartTask.finish();

        List<SessionClientCommand> abortList = new ArrayList<SessionClientCommand>();

        synchronized (this) {
            /*
             * Command dispatch may have selected a transport just before it is detached, in which case, a command
             * may have been put in the active set while on its way to a dead transport. Such commands should fail
             * shortly at the transport. We will wait briefly here for them to drain before proceeding.
             */
            while (!activeSet.isEmpty() || !abortSet.isEmpty()) {
                logger.infof("%s: drain active (%d/%d) commands", nexus, activeSet.size(), abortSet.size());

                try {
                    wait(1000);
                } catch (InterruptedException e) {
                    // Do nothing
                }
            }

            /*
             * The stale set includes all the commands that have been completed prematurely without the channel state
             * fully synchronized. The abort queue includes all the commands that need to be aborted, some stale (say
             * from previous channel disconnect) and others not. We only ever want to invoke the command completion
             * callback once. Hence, we should look for commands in the abort queue that aren't in the stale set to
             * complete.
             */
            for (SessionClientCommand command : abortQueue) {
                if (!staleSet.contains(command)) {
                    abortList.add(command);
                }
            }

            staleSet.addAll(abortList);
        }

        for (SessionClientCommand command : abortList) {
            logger.errorf("%s: aborted command completed after channel disconnect", command);
            commandReset(command);
        }

        abortList.clear();

        // Unblock shutdown matching block during attach
        shutdown.unblock();

        if (nexus.isClosed()) {
            logger.infof("%s: channel disconnected", nexus);
        } else {
            logger.errorf("%s: channel disconnected", nexus);
        }
    }

    /**
     * Shutdown the channel. Channel shutdown is initiated from the session after all transports have been withdrawn
     * either due to irrecoverable transport failures or session reset. Consequently, transports are being detached
     * from the channel if not already. We will wait for the channel is quiesced before proceeding with the shutdown.
     */
    @Override
    public void shutdown() {
        logger.infof("%s: channel shutdown started", nexus);

        // Start the shutdown process to ensure the channel is fully quiesced
        for (;;) {
            try {
                shutdown.start();
                break;
            } catch (InterruptedException e) {
                // Do nothing
            }
        }

        // Complete all pending commands
        logger.infof("%s: abort %d pending commands", nexus, pendingQueue.size());

        for (SessionClientCommand command : pendingQueue) {
            commandReset(command);
        }

        // Complete all retry commands
        logger.infof("%s: abort %d retry commands", nexus, retryQueue.size());

        for (SessionClientCommand command : retryQueue) {
            commandReset(command);
        }

        /*
         * The channel should have been completely quiesced by now with the sole exception of stats collection. The
         * parent session may have been obtained before it's closed, in which case, we may still need to access the
         * various queues for a snapshot of the channel stats. The synchronized block is added to guard against that.
         */
        synchronized (this) {
            registry.removeAll(pendingQueue);
            pendingQueue.clear();

            registry.removeAll(retryQueue);
            retryQueue.clear();

            // The stale set should be a super set of abort queue after channel disconnected
            assert staleSet.containsAll(abortQueue);

            // Clear the abort queue
            abortQueue.clear();

            /*
             * Clear the stale set which are already completed. Note that after this point the channel state will be
             * inconsistent and there is no way to reconcile any more. And that is fine for shutdown.
             */
            registry.removeAll(staleSet);
            staleSet.clear();

            assert registry.isEmpty();
        }

        /*
         * If logout has been attempted it should have been completed by now. Logout is only initiated from the
         * session under some circumstances and the session will not proceed to channel shutdown until logout is
         * completed.
         */
        if (logout != null) {
            assert logout.getFuture().isDone() : "logout still in progress";
        }

        logger.infof("%s: channel shutdown completed", nexus);
    }

    private abstract class SessionClientTask extends AsyncTask<SessionClientCommand> {

        public SessionClientTask(Queue<SessionClientCommand> queue) {
            super(queue, SessionClientChannel.this, nexus.getExecutor());
        }

        @Override
        protected boolean isReady() {
            return isConnected();
        }
    }

    private double getCompressionRatio() {
        return stats.getCompressionRatio();
    }
}
