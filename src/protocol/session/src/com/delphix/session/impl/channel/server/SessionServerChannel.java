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

package com.delphix.session.impl.channel.server;

import com.delphix.session.impl.common.*;
import com.delphix.session.impl.frame.*;
import com.delphix.session.util.AsyncTask;
import com.delphix.session.util.ObjectRegistry;
import com.delphix.session.util.TaskMgmtSync;

import java.util.*;
import java.util.concurrent.Future;

public class SessionServerChannel extends SessionChannel {

    // Synchronous pending restart threshold
    private static final int PENDING_THRESHOLD = 4;

    // Attached transports
    private List<SessionTransport> xports = new ArrayList<SessionTransport>();

    // Command registry for all outstanding commands
    private final ObjectRegistry<ExchangeID, SessionServerCommand> registry = ObjectRegistry.create();

    private SessionServerSlotTable slotTable; // Command slot table
    private SessionServerSequencer sequencer; // Command sequencer

    /*
     * Each channel imposes a limit on the total number of outstanding commands. The limit is directly reflected
     * in the size of the slot table, which leads to the maximum command sequence expected at any given moment. The
     * size of the slot table is subject to change due to resource availability but is generally stable. The maximum
     * command sequence, however, is much more fluid as it changes with each command submission and completion.
     *
     *   maximumCommandSN = expectedCommandSN + availableSlots - 1
     */
    private SerialNumber maximumCommandSN; // Maximum command sequence allowed

    /*
     * We learn client command sequence usage from each request coming in the channel. Each command has a sequence
     * allocated to itself while other operate requests carry snapshot of the latest command sequence. Responses from
     * the sibling channel also carry the latest command sequence of this channel. We keep track of the latest command
     * sequence since the distance from the expected command sequence reveals quality of delivery. The latest command
     * sequence must never exceed maximum command sequence.
     */
    private SerialNumber latestCommandSN; // Latest command sequence allocated by client

    // Command actively being executed
    private final Set<SessionServerCommand> activeSet = new HashSet<SessionServerCommand>();

    // Commands waiting for retries to be responded to
    private final Queue<SessionServerCommand> retryQueue = new LinkedList<SessionServerCommand>();

    // Commands waiting for task management to be responded to
    private final Queue<SessionServerCommand> abortQueue = new LinkedList<SessionServerCommand>();

    private final SessionServerTask retryTask; // Retry task
    private final SessionServerTask abortTask; // Abort task
    private final SessionServerTask restartTask; // Restart task

    /*
     * Commands that are failed with slot related errors are not kept in the registry because the normal slot table
     * mechanism doesn't apply here when it comes to cache management. But we'd still want task management response
     * for such commands to be ordered properly. We use a separate asynchronous task queue just for that.
     */
    private final Queue<SessionExchange> errorQueue = new LinkedList<SessionExchange>();

    private final AsyncTask<SessionExchange> errorTask; // Error task

    private final TaskMgmtSync shutdown; // Channel shutdown sync
    private SessionServerLogout logout; // Session logout exchange

    private final SessionServerChannelStats stats; // Channel stats

    public SessionServerChannel(SessionNexus nexus, boolean fore, int queueDepth, SerialNumber commandSN) {
        super(nexus, fore);

        // Create the slot table
        slotTable = new SessionServerSlotTable(queueDepth);

        // Initialize the command sequence numbers with latestCommandSN starting off as null
        sequencer = new SessionServerSequencer(commandSN, queueDepth);

        expectedCommandSN = commandSN;
        maximumCommandSN = expectedCommandSN.next(slotTable.getCurrentMaxSlotID());

        stats = new SessionServerChannelStats(this);

        shutdown = new TaskMgmtSync();

        // Fire up the retry task
        retryTask = new SessionServerTask(retryQueue) {

            @Override
            protected void doWork(SessionServerCommand command) {
                retryCommand(command);
            }
        };

        // Fire up the abort task
        abortTask = new SessionServerTask(abortQueue) {

            @Override
            protected void doWork(SessionServerCommand command) {
                abortCommand(command);
            }
        };

        // Fire up the restart task
        Queue<SessionServerCommand> pendingQueue = sequencer.getPendingQueue();

        restartTask = new SessionServerTask(pendingQueue) {

            @Override
            protected void doWork(SessionServerCommand command) {
                restartCommand(command);
            }
        };

        // Fire up the error task
        errorTask = new SessionErrorTask(errorQueue);
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
        return false;
    }

    @Override
    public void setExpectedCommandSN(SerialNumber commandSN) {
        updateExpectedCommandSN(commandSN, 1);
    }

    private void updateExpectedCommandSN(SerialNumber commandSN, int size) {
        assert expectedCommandSN.equals(commandSN);
        expectedCommandSN = commandSN.next(size);
    }

    public SerialNumber getLatestCommandSN() {
        return latestCommandSN;
    }

    private void setLatestCommandSN(SerialNumber commandSN) {
        if (latestCommandSN == null || latestCommandSN.lessThan(commandSN)) {
            latestCommandSN = commandSN;
        }
    }

    public SerialNumber getMaximumCommandSN() {
        return maximumCommandSN;
    }

    private void updateMaximumCommandSN() {
        maximumCommandSN = maximumCommandSN.next();
    }

    @Override
    public void refresh(RequestFrame request) {
        if (request instanceof CommandRequest) {
            return;
        }

        synchronized (this) {
            setLatestCommandSN(request.getCommandSN());
        }
    }

    @Override
    public synchronized void refresh(ResponseFrame response) {
        setLatestCommandSN(response.getCommandSN());
    }

    @Override
    public synchronized void update(RequestFrame request) {
        request.setExpectedCommandSN(getExpectedCommandSN());
    }

    @Override
    public synchronized void update(ResponseFrame response) {
        response.setExpectedCommandSN(getExpectedCommandSN());

        response.setCurrentMaxSlotID(slotTable.getCurrentMaxSlotID());
        response.setTargetMaxSlotID(slotTable.getTargetMaxSlotID());
    }

    public int getActiveCommands() {
        return activeSet.size();
    }

    public int getCachedCommands() {
        return registry.size();
    }

    public int getUndoneCommands() {
        return slotTable.inUse();
    }

    public SessionExchange createExchange(RequestFrame request) {
        SessionExchange exchange;

        // Create an exchange based on the incoming request type
        if (request instanceof CommandRequest) {
            exchange = new SessionServerCommand(this, request);
        } else if (request instanceof TaskMgmtRequest) {
            exchange = new SessionServerAbort(this, request);
        } else if (request instanceof PingRequest) {
            exchange = new SessionServerPing(this, request);
        } else if (request instanceof LogoutRequest) {
            exchange = new SessionServerLogout(this, request);
        } else {
            throw new ProtocolViolationException("invalid message " + request);
        }

        return exchange;
    }

    public synchronized void exchangeReceived(SessionServerAbort abort) {
        TaskMgmtRequest request = (TaskMgmtRequest) abort.getRequest();

        SerialNumber targetCommandSN = request.getTargetCommandSN();
        ExchangeID targetExchangeID = request.getTargetExchangeID();

        /*
         * On the client, task management is only initiated after the target command has been started by the session.
         * Command start here implies the assignment of a command sequence and a slot. Before the command is started,
         * it would have been aborted locally on the client without the need for task management.
         *
         * The command sequence of the task management request is a snapshot of the most recent commandSN taken at the
         * time the task management was sent. As a result, the target command sequence must never be greater than that
         * of the task management. Otherwise, it would be a protocol violation.
         */
        if (targetCommandSN.greaterThan(request.getCommandSN())) {
            throw new ProtocolViolationException(targetCommandSN + " exceeds " + request.getCommandSN());
        }

        /*
         * The target commandSN must never exceed maximum commandSN. Otherwise, it is a protocol violation.
         */
        if (targetCommandSN.greaterThan(maximumCommandSN)) {
            throw new ProtocolViolationException(targetCommandSN + " exceeds " + maximumCommandSN);
        }

        SessionServerCommand command = registry.locate(targetExchangeID);

        if (command != null) {
            logger.infof("%s: task management on command in %s state", abort, command.getState());

            command.offerAbort(abort);
            abort.setCommand(command);

            abort(command);

            return;
        }

        /*
         * Target command that precedes the expected command sequence must have been received. Such command is kept
         * in the registry until the status of the command is confirmed by the peer via either normal acknowledgment
         * or task management. The only exception to that is if the command received earlier was failed because of
         * slot related error, in which case, the command is never registered since the regular slot table mechanism
         * won't help with cache management. A more elaborate case involves a command that is cached but purged soon
         * before task management arrives, due to a race between task management and a new request over a different
         * transport after the command response is received. We will treat both as if they were slot failures. The
         * peer will not process the task management response in the second case though after the command response
         * has been received.
         */
        if (targetCommandSN.lessThan(expectedCommandSN)) {
            logger.infof("%s: task management on command already purged", abort);

            // Set the task management status
            abort.setTaskMgmtStatus(TaskMgmtStatus.ABORTED_SLOT_FAILURE);

            // Submit the abort to the error task for response serialization
            errorTask.submit(abort);

            return;
        }

        /*
         * Task management has been received before the target command itself. The target command may arrive later
         * over a different transport or it may never. Also, task management may be retried in case the response is
         * lost. Whether the target command is received or not, the channel state has been modified as indicated by
         * the task management. A "ghost" command is instantiated until the channel state is back in sync.
         */
        logger.infof("%s: task management on command %s (expected %s) before arrival", abort,
                targetCommandSN, expectedCommandSN);

        command = new SessionServerCommand(this, abort);

        // Register the "ghost" command by the target exchange ID
        registry.register(targetExchangeID, command);

        // Start command processing by subjecting it to sequencing
        sequence(command);
    }

    /**
     * State transitions: T6, T12
     *
     * Process an incoming task management request on the given command.
     */
    private void abort(SessionServerCommand command) {
        SessionServerCommandState state = command.getState();

        switch (state) {
        case INDOUBT:
            command.setTaskMgmtStatus(TaskMgmtStatus.ALREADY_COMPLETED);
            command.setState(SessionServerCommandState.ABORT);
            break;

        case ABORTED:
            command.setState(SessionServerCommandState.ABORT);
            break;

        case ACTIVE:
            command.getFuture().cancel(true, false);

            // FALLTHRU
        case PENDING:
        case ABORT:
        case RETRY:
            return;

        default:
            throw new IllegalStateException("invalid command state " + state);
        }

        // Submit the command to the abort task
        abortTask.submit(command);
    }

    /**
     * State transitions: T9, T11
     *
     * Process queued task management requests. This is invoked from the async abort task. The command state should
     * be ABORT before it is submitted to the abort task.
     */
    private void abortCommand(SessionServerCommand command) {
        for (;;) {
            SessionServerAbort abort;

            synchronized (this) {
                abort = command.pollAbort();

                if (abort == null) {
                    command.setState(SessionServerCommandState.ABORTED);

                    // Command may have been evicted while going through abort
                    if (!command.hasSlot()) {
                        finalize(command);
                    }

                    return;
                }
            }

            abort.send();
        }
    }

    /**
     * State transitions: T5
     *
     * Process an incoming command exchange which turns out to be a retry.
     */
    private void retry(SessionServerCommand original, SessionServerCommand duplicate) {
        logger.infof("%s: command retry in state %s over %s", original, original.getState(),
                duplicate.getTransport());

        switch (original.getState()) {
        case PENDING:
        case ACTIVE:
            original.offerRetry(duplicate);
            duplicate.setPrimary(original);
            break;

        case INDOUBT:
            original.setState(SessionServerCommandState.RETRY);

            original.offerRetry(duplicate);
            duplicate.setPrimary(original);

            retryTask.submit(original);

            break;

        default:
            /*
             * The client promises to never send a retry after it has initiated task management on the command. If we
             * get a retry after the command has being aborted, it could only have come from a "stale" transport, or
             * one that has been found to be dead on the client.
             */
            throw new TransportStaleException("failed to resend response");
        }
    }

    /**
     * State transitions: T4, T6, T10
     *
     * Process queued command retries. This is invoked from the async retry task. The command state should be RETRY
     * before it is submitted to the retry task.
     */
    public void retryCommand(SessionServerCommand command) {
        for (;;) {
            SessionServerCommand retry;

            synchronized (this) {
                retry = command.pollRetry();

                if (retry == null) {
                    // Set the command back to INDOUBT now that retry is over
                    command.setState(SessionServerCommandState.INDOUBT);

                    // Schedule the command for abort processing
                    if (command.hasAbort()) {
                        // The command is pending abort while it is active
                        command.setState(SessionServerCommandState.ABORT);

                        // Set the task management status according to the future execution status
                        Future<?> future = command.getFuture();

                        if (future == null) {
                            command.setTaskMgmtStatus(TaskMgmtStatus.ABORTED_BEFORE_START);
                        } else if (future.isCancelled()) {
                            command.setTaskMgmtStatus(TaskMgmtStatus.ABORTED_AFTER_START);
                        } else {
                            command.setTaskMgmtStatus(TaskMgmtStatus.ALREADY_COMPLETED);
                        }

                        // Submit the command to the abort task
                        abortTask.submit(command);

                        return;
                    }

                    // Command may have been evicted while going through retry
                    if (!command.hasSlot()) {
                        finalize(command);
                    }

                    return;
                }
            }

            // Only send if the command has not been cancelled
            if (!command.getFuture().isCancelled()) {
                retry.send();
            }
        }
    }

    /**
     * A command has been received from the transport. This is invoked from the transport IO context. Prior to this
     * callback, a command exchange has been initiated with createExchange call. Hence, the command exchange should
     * be in the INITIAL state upon entry.
     */
    public synchronized void exchangeReceived(SessionServerCommand command) {
        ExchangeID exchangeID = command.getExchangeID();
        SerialNumber commandSN = command.getCommandSN();

        /*
         * The target commandSN must never exceed maximum commandSN. Otherwise, it is a protocol violation.
         */
        if (commandSN.greaterThan(maximumCommandSN)) {
            throw new ProtocolViolationException(commandSN + " exceeds " + maximumCommandSN);
        }

        // Handle command retry if found to be a duplicate
        SessionServerCommand original = registry.locate(exchangeID);

        if (original != null) {
            retry(original, command);
            return;
        }

        /*
         * Due to the fact that the client and server do not synchronize transport state it is possible that the server
         * processes a command over a stale transport well after a re-transmission of that command has been processed
         * over a healthy transport.  This means that even for a well behaved client we could receive a command whose
         * session level sequence number is less than the next expected sequence number.  In this case it is safe to
         * drop the command since we must have already processed a retransmission of this command.
         */
        if (commandSN.lessThan(expectedCommandSN)) {
            logger.debugf("Dropping command %s SN %s on xport %s (expected SN: %s)", command, commandSN,
                    command.getTransport(), expectedCommandSN);
            return;
        }

        // Register the new command by the exchange ID
        registry.register(exchangeID, command);

        // Start command processing by subject it to sequencing
        sequence(command);
    }

    /**
     * State transitions: T1, T7
     *
     * Subject a new command to sequencing. All new commands are put through sequencing as soon as they come into
     * existence. Normally, a new command is what has just arrived on a transport. An exception to that is the so
     * called "ghost" command created by task management that arrived before the target command. In either case, we
     * must subject the command to sequencing to unblock the sequence queue as well as for slot maintenance.
     */
    private void sequence(SessionServerCommand command) {
        // First set the command to the PENDING state from the INITIAL state
        command.setState(SessionServerCommandState.PENDING);

        /*
         * Reserve the slot for the command. Doing so allows us to detect nasty slot failures up front. Whether the
         * command is to be responded to or aborted later, we would not miss checking the slot status. It also allows
         * the slot status to be confirmed as soon as possible to free up unnecessary cached commands.
         *
         * The slot must be released prior to the first attempt to send any form of response back, whether it is
         * command response or task management response. That is because the client may issue the next command over
         * the same slot if and when it receives the response.
         */
        slotTable.reserve(command);

        if (!command.hasSlot()) {
            logger.errorf("%s: command slot reservation failed (%s)", command, command.getStatus());
        }

        /*
         * Check whether it is the next command expected by the sequencer. If not, the sequence queue state is not
         * changed (from waiting to ready) as a result of the new command.
         */
        int count = sequencer.enter(command);

        if (count == 0) {
            return;
        }

        // Advance expected command sequence
        updateExpectedCommandSN(command.getCommandSN(), count);

        /*
         * The queue state may have changed due to the new command. If the restart task is still active, let it take
         * care of the pending queue.
         */
        if (!restartTask.isDone()) {
            return;
        }

        /*
         * Submit the restart task to process the pending queue if the number of pending commands due to out of order
         * delivery has exceeded the threshold for synchronous processing.
         */
        Queue<SessionServerCommand> queue = sequencer.getPendingQueue();

        if (queue.size() > PENDING_THRESHOLD) {
            restartTask.submit();
            return;
        }

        /*
         * Process the command synchronously in the current context. Doing so should help with command latency if
         * delivery is mostly in order. A special case of guaranteed in order delivery involves a single transport
         * configuration where the queue size is always one.
         *
         * The pending queue is sorted by command SN, which is the order we would like to process commands. However,
         * the new command may not always be the first in the pending queue. The following describes such a scenario.
         * The command n is entered for asynchronous restart after the restart task has exited its main loop but right
         * before it has marked itself done. The new command n + 1 is entered after the restart task has marked itself
         * done but before the rescheduling takes place in the task done callback.
         *
         *        restart task            command n            command n + 1
         *        ------------            ---------            -------------
         *        pending queue empty
         *        exit task loop
         *                                enter command
         *                                async restart
         *        mark task done
         *                                                     enter command
         *                                                     sync process
         *        reschedule
         */
        SessionServerCommand next;

        while ((next = queue.poll()) != null) {
            if (next == command) {
                process(next);
            } else {
                restart(next);
            }
        }
    }

    /**
     * State transitions: T2, T7, T8
     *
     * Process the given command after it is subject to the sequencer. This may be invoked inline in the network IO
     * thread context or the async restart task.
     */
    private void process(SessionServerCommand command) {
        if (command.hasAbort()) {
            /*
             * The command had been aborted while it was pending. We had to defer abort processing until the command
             * sequencing is satisfied. Once we responded to the abort request, it would enable the client to start
             * the next command at the next commandSN immediately. We don't want to do that to a pending command as
             * it would cause the sequencer to overflow.
             */
            if (command.hasSlot()) {
                command.setTaskMgmtStatus(TaskMgmtStatus.ABORTED_BEFORE_START);

                /*
                 * The slot has been reserved on behalf of the command upon arrival. We must release the slot before
                 * sending the task management response because the next command over the same slot may arrive at any
                 * time after that.
                 */
                slotTable.release(command);
            } else {
                command.setTaskMgmtStatus(TaskMgmtStatus.ABORTED_SLOT_FAILURE);
            }

            // Advance the maximumCommandSN
            updateMaximumCommandSN();

            command.setState(SessionServerCommandState.ABORT);

            abortTask.submit(command);

            return;
        }

        // Activate the command
        command.setState(SessionServerCommandState.ACTIVE);

        /*
         * Slot failure is nasty and must be handled with care. Specifically, commands with slot failure are not kept
         * in the registry nor do they move the slotSN forward. The slot status must be communicated to the client in
         * no uncertain terms, whether in the form of command response or task management response, so the slotSN can
         * be adjusted accordingly.
         */
        if (!command.hasSlot()) {
            logger.errorf("%s: command failed with slot error", command);

            // Advance the maximumCommandSN
            updateMaximumCommandSN();

            // Finalize the command
            command.setState(SessionServerCommandState.FINAL);

            registry.unregister(command.getExchangeID());

            // Submit the command to the error task
            errorTask.submit(command);

            // Process retries if any
            command = command.pollRetry();

            while (command != null) {
                errorTask.submit(command);
                command = command.pollRetry();
            }

            return;
        }

        // Add the command to the active set before submitting for execution
        boolean added = activeSet.add(command);
        assert added;

        // Finally subject the command to execution
        command.invoke();
    }

    private synchronized void restartCommand(SessionServerCommand command) {
        restart(command);
    }

    private void restart(SessionServerCommand command) {
        // (Re)start the command
        command.start();
        process(command);
    }

    /**
     * Service invocation callback. It is invoked in the same thread context as the one that executes the service.
     * This is only invoked on the primary command instance, which is the only command instance that causes the
     * service to be invoked.
     */
    public void respondCommand(SessionServerCommand command) {
        synchronized (this) {
            /*
             * Release the slot reserved on behalf of the command. It must be done prior to sending the response back
             * because the next command may come in at any moment after that.
             */
            slotTable.release(command);

            // Advance the maximumCommandSN before responding for the same reason as above.
            updateMaximumCommandSN();
        }

        // Only send if the command has not been cancelled
        if (!command.getFuture().isCancelled()) {
            command.send();
        }

        completeCommand(command);
    }

    /**
     * State transitions: T3, T5, T6
     *
     * Complete an ACTIVE command after it returns from the service invocation. This is invoked at most once for a
     * command on the primary instance. In the normal case, the command will transition to INDOUBT state and wait
     * for confirmation to come. If it has retries or aborts queued while it was active, it would transition to the
     * RETRY or ABORT state, respectively, with the corresponding async task scheduled.
     */
    public synchronized void completeCommand(SessionServerCommand command) {
        // Remove the command from the active set
        boolean removed = activeSet.remove(command);
        assert removed;

        command.setState(SessionServerCommandState.INDOUBT);

        if (command.hasRetry()) {
            command.setState(SessionServerCommandState.RETRY);
            retryTask.submit(command);
            return;
        }

        if (command.hasAbort()) {
            // The command is pending abort while it is active
            command.setState(SessionServerCommandState.ABORT);

            // Set the task management status according to the future execution status
            Future<?> future = command.getFuture();

            if (future.isCancelled()) {
                command.setTaskMgmtStatus(TaskMgmtStatus.ABORTED_AFTER_START);
            } else {
                command.setTaskMgmtStatus(TaskMgmtStatus.ALREADY_COMPLETED);
            }

            // Submit the command to the abort task
            abortTask.submit(command);

            return;
        }

        // Finalize the command if it has been evicted by the next command over the same slot
        if (!command.hasSlot()) {
            finalize(command);
        }
    }

    /**
     * State transitions: T4, T9
     *
     * Finalize the command. After this point, the command is history and no longer known to the channel. This is
     * done only after client has confirmed the command state, whether completed or aborted. Also, a command is only
     * finalized from one of the two quiesced states. This is always called with the channel locked.
     */
    private void finalize(SessionServerCommand command) {
        // Set the command state to FINAL
        command.setState(SessionServerCommandState.FINAL);

        registry.unregister(command.getExchangeID());

        // Update the channel stats
        stats.update(command);
    }

    /**
     * Evict the command from the registry after we have confirmation that the client is in sync with us regarding
     * the status of the command. This is invoked from the slot as the next command arrives with the channel locked.
     */
    public void evict(SessionServerCommand command) {
        SessionServerCommandState state = command.getState();

        // Reset command slot
        assert command.hasSlot();
        command.resetSlot();

        switch (state) {
        case INDOUBT:
        case ABORTED:
            // Command has been quiesced so is ready to be finalized
            finalize(command);
            break;

        case ABORT:
        case RETRY:
        case ACTIVE:
            /*
             * Command has not been quiesced yet so defer finalization til it is. The command may be in ACTIVE state
             * only if the next command raced with its completion, which happens once in a while over a fast network
             * such as the loopback.
             */
            break;

        default:
            throw new IllegalStateException("invalid command state " + state);
        }
    }

    @Override
    public boolean isConnected() {
        return !xports.isEmpty();
    }

    @Override
    protected void attach(SessionTransport xport) {
        synchronized (this) {
            logger.infof("%s: %s attached to channel", nexus, xport);

            boolean disconnected = xports.isEmpty();

            xports.add(xport);

            if (disconnected) {
                // Resume the asynchronous channel tasks
                abortTask.submit();
                retryTask.submit();
                errorTask.submit();
                restartTask.submit();

                // Block shutdown which should not have started yet
                boolean status = shutdown.block();
                assert status;

                logger.infof("%s: channel restored", nexus);
            }
        }

        // Notify the transport that new requests are now accepted
        xport.notifyAccepted();
    }

    @Override
    protected void detach(SessionTransport xport) {
        logger.infof("%s: %s detached from the channel", nexus, xport);

        synchronized (this) {
            xports.remove(xport);

            if (!xports.isEmpty()) {
                return;
            }
        }

        // Quiesce the asynchronous channel tasks
        abortTask.finish();
        retryTask.finish();
        errorTask.finish();
        restartTask.finish();

        /*
         * In fact, it is possible that some commands may still be active at this point. Since the session has just
         * lost all the transports, we want to keep those commands running in hopes that it may be continued by the
         * client later. If the session is to be reset without continuation, the active commands shall be aborted
         * and quiesced then.
         */

        // Unblock shutdown matching block during attach
        shutdown.unblock();

        if (nexus.isClosed()) {
            logger.infof("%s: channel disconnected", nexus);
        } else {
            logger.errorf("%s: channel disconnected", nexus);
        }
    }

    @Override
    public void shutdown() {
        logger.infof("%s: channel shutdown started", nexus);

        // Start the shutdown process to ensure the channel is fully quiesced
        for (;;) {
            try {
                shutdown.start();
                break;
            } catch (InterruptedException e1) {
                // Do nothing
            }
        }

        // Wait for the channel to be quiesced from detach
        synchronized (this) {
            /*
             * Cancel all active commands. For applications that require strict ordering in command delivery as well
             * as execution, commands may be queued up in the command SN order in the application specified executor
             * and executed in a serialized manner. We will cancel commands in the reverse order of the command SN so
             * that no new commands are started after a previous one is cancelled.
             */
            logger.infof("%s: abort %d active commands", nexus, activeSet.size());

            List<SessionServerCommand> activeList = new ArrayList<SessionServerCommand>(activeSet);

            Collections.sort(activeList, new Comparator<SessionServerCommand>() {

                @Override
                public int compare(SessionServerCommand a, SessionServerCommand b) {
                    return b.getCommandSN().compareTo(a.getCommandSN());
                }
            });

            for (SessionServerCommand command : activeList) {
                command.getFuture().cancel(true, false);
            }

            // Wait for active commands to come in
            while (!activeSet.isEmpty()) {
                logger.infof("%s: wait for active (%d) to complete", nexus, activeSet.size());

                try {
                    wait(1000);
                } catch (InterruptedException e) {
                    // Do nothing
                }
            }

            // Clear the async task queues
            retryQueue.clear();
            abortQueue.clear();
            errorQueue.clear();

            // Finally clear the registry
            registry.clear();
        }

        logger.infof("%s: channel shutdown completed", nexus);
    }

    public synchronized void exchangeReceived(SessionServerLogout request) {
        logger.infof("%s: logout received", nexus);

        if (logout != null) {
            logout.offerRetry(request);
            request.setPrimary(logout);
            return;
        }

        // Keep track of the active logout request
        logout = request;

        logout.invoke();
    }

    public void logout() {
        logger.infof("%s: proceed to logout", nexus);

        assert logout != null : "logout inactive";
        logout.send();

        for (;;) {
            SessionServerLogout retry;

            synchronized (this) {
                retry = logout.pollRetry();

                if (retry == null) {
                    break;
                }
            }

            retry.send();
        }
    }

    private abstract class SessionServerTask extends AsyncTask<SessionServerCommand> {

        public SessionServerTask(Queue<SessionServerCommand> queue) {
            super(queue, SessionServerChannel.this, nexus.getExecutor());
        }

        @Override
        protected boolean isReady() {
            return isConnected();
        }
    }

    private class SessionErrorTask extends AsyncTask<SessionExchange> {

        public SessionErrorTask(Queue<SessionExchange> queue) {
            super(queue, SessionServerChannel.this, nexus.getExecutor());
        }

        @Override
        protected void doWork(SessionExchange exchange) {
            exchange.send();
        }

        @Override
        protected boolean isReady() {
            return isConnected();
        }
    }
}
