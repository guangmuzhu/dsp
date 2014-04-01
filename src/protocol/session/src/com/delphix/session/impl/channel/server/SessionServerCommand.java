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

import com.delphix.session.impl.common.SessionNexus;
import com.delphix.session.impl.frame.*;
import com.delphix.session.service.ServiceException;
import com.delphix.session.service.ServiceExecutionException;
import com.delphix.session.service.ServiceRequest;
import com.delphix.session.service.ServiceResponse;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

/**
 * Transport allegiance refers to the fixed binding that a command exchange has to the transport, i.e., a specific
 * command instance is bound to the transport for life starting from when the request arrives until the response is
 * sent. Transport allegiance is strictly observed on the server. The server must never break the allegiance on its
 * own, say by resending the response over a different transport than the one the request came in on.
 *
 * Meanwhile, the client is driving the command retry and task management process in case of failure. A retry, while
 * referring to the same logical command, is considered a separate command instance as far as transport allegiance is
 * concerned. The server holds together all related command instances under a single primary command. The primary
 * command is instantiated on the server when the logical command is first received over a transport.
 *
 * The ordering of the related command instances is tricky business. While the client promises to never retry the
 * same command until the transport that it was last sent is dead, the order in which the same transports are found
 * dead on the server may be completely different. Worse yet, a transport detected dead on the client may very well
 * still be "alive" (i.e., delivering data) on the server. Since the protocol design does not require transport state
 * synchronization after a transport is found dead and before command allegiant to that transport may be retried, the
 * server needs to deal with command instances arriving on "stale" transports. It is also possible for those "stale"
 * instances to race to the "fresh" instance since they are from different transports.
 *
 * With the "stale" transport issue, the server won't know for certain whether a command instance is the latest or
 * not so it has to respond to all of them. Of course, the command is never actually executed more than once. It does
 * not introduce correctness issues, as long as the client adheres to the "do-not-retry-til-transport-is-dead" rule.
 *
 * Note: an alternative design is possible that is immune from the "stale" transport issue described above. The trade
 * off though is a more complex control path. Specifically, more states would be needed to support transport state
 * synchronization.
 */
public class SessionServerCommand extends SessionServerExchange {

    private SessionServerSlot slot; // Command slot
    private SerialNumber commandSN; // Command sequence

    private CommandStatus commandStatus; // Command status

    private SessionServerCommandState state; // Command state
    private SessionServerFuture future; // Command future

    private SessionServerCommand primary; // Primary command

    private Queue<SessionServerCommand> retryQueue; // Pending retry queue
    private Queue<SessionServerAbort> abortQueue; // Pending abort queue

    private TaskMgmtStatus taskMgmtStatus; // Task management status

    private final SessionServerCommandStats stats; // Command stats

    public SessionServerCommand(SessionServerChannel channel, RequestFrame request) {
        super(channel, request);

        this.commandSN = request.getCommandSN();

        // Initialize the command state
        setState(SessionServerCommandState.INITIAL);

        stats = new SessionServerCommandStats();
    }

    /**
     * Create a "ghost" command from the task management exchange. The target of the task management has not yet been
     * received and may never be. The "ghost" command includes minimal information that is required for channel state
     * maintenance.
     */
    public SessionServerCommand(SessionServerChannel channel, SessionServerAbort abort) {
        super(channel);

        TaskMgmtRequest request = (TaskMgmtRequest) abort.getRequest();

        SerialNumber commandSN = request.getTargetCommandSN();
        ExchangeID exchangeID = request.getTargetExchangeID();

        this.commandSN = commandSN;
        this.exchangeID = exchangeID;

        // Initialize the command state
        setState(SessionServerCommandState.INITIAL);

        stats = new SessionServerCommandStats();

        offerAbort(abort);
        abort.setCommand(this);
    }

    public ServiceRequest getServiceRequest() {
        CommandRequest request = (CommandRequest) getRequest();
        return request.getRequest();
    }

    public SessionServerCommandState getState() {
        return state;
    }

    public void setState(SessionServerCommandState newState) {
        SessionServerCommandState oldState = state;

        SessionServerCommandState.validate(oldState, newState);
        state = newState;

        logger.tracef("%s: state transition %s -> %s", this, oldState, newState);
    }

    public SessionServerSlot getSlot() {
        return slot;
    }

    public void setSlot(SessionServerSlot slot) {
        this.slot = slot;
    }

    public void resetSlot() {
        setSlot(null);
    }

    public boolean hasSlot() {
        return slot != null;
    }

    public int getSlotID() {
        CommandRequest request = (CommandRequest) getRequest();

        if (request != null) {
            return request.getSlotID();
        }

        SessionServerAbort abort = abortQueue.peek();
        TaskMgmtRequest taskMgmt = (TaskMgmtRequest) abort.getRequest();

        return taskMgmt.getTargetSlotID();
    }

    public SerialNumber getSlotSN() {
        CommandRequest request = (CommandRequest) getRequest();

        if (request != null) {
            return request.getSlotSN();
        }

        SessionServerAbort abort = abortQueue.peek();
        TaskMgmtRequest taskMgmt = (TaskMgmtRequest) abort.getRequest();

        return taskMgmt.getTargetSlotSN();
    }

    public int getMaxSlotIDInUse() {
        CommandRequest request = (CommandRequest) getRequest();

        if (request != null) {
            return request.getMaxSlotIDInUse();
        }

        SessionServerAbort abort = abortQueue.peek();
        TaskMgmtRequest taskMgmt = (TaskMgmtRequest) abort.getRequest();

        return taskMgmt.getMaxSlotIDInUse();
    }

    public SerialNumber getCommandSN() {
        return commandSN;
    }

    public void setCommandSN(SerialNumber commandSN) {
        this.commandSN = commandSN;
    }

    public CommandStatus getCommandStatus() {
        return commandStatus;
    }

    public void setCommandStatus(CommandStatus status) {
        this.commandStatus = status;
    }

    public SessionServerFuture getFuture() {
        return future;
    }

    /**
     * Invoke the service for execution. Depending on the executor service associated with the nexus, the command
     * may be processed in the same or different thread context. If it is processed in a different thread, it is
     * the responsibility of the application to ensure ordered command execution, such as through a serializable
     * executor service. If it uses the same thread, keep in mind this is the network IO context so the work has
     * to be brief to avoid data queuing up in the transport. By default, if an executor service is not specified
     * during service registration, it will used the thread pool provided by the service manager, which does not
     * hold ordering guarantee.
     */
    public void invoke() {
        CommandRequest request = (CommandRequest) getRequest();

        final ServiceRequest serviceRequest = request.getRequest();
        final SessionNexus nexus = channel.getNexus();

        // Create the callable for the service invocation
        Callable<ServiceResponse> callable = new Callable<ServiceResponse>() {

            @Override
            public ServiceResponse call() throws Exception {
                logger.tracef("%s: execute request %s", nexus, serviceRequest);
                ServiceResponse response = serviceRequest.execute(nexus);
                logger.tracef("%s: return response %s", nexus, response);
                return response;
            }
        };

        // Instantiate the server future from the callable with callback
        future = new SessionServerFuture(this, callable) {

            @Override
            protected void done() {
                finish();
            }
        };

        // Add the command to the active set before submitting for execution
        setCommandStatus(CommandStatus.SUCCESS);

        // Time service invoke
        stats.invoke();

        // Submit the future for execution
        nexus.getExecutor().execute(future);
    }

    private void finish() {
        // Time service finish
        stats.finish();

        /*
         * Now that the command has been serviced, the service request is not needed any more since we will never
         * execute the same service request again. We could compute a fingerprint over the content of the request
         * to prevent any false retry if necessary. But for now, we simply trust the client.
         */
        CommandRequest request = (CommandRequest) getRequest();
        request.setRequest(null);

        // Response to the client
        channel.respondCommand(this);
    }

    public SessionServerCommand getPrimary() {
        return primary;
    }

    public void setPrimary(SessionServerCommand primary) {
        this.primary = primary;
    }

    public void offerRetry(SessionServerCommand command) {
        if (retryQueue == null) {
            retryQueue = new LinkedList<SessionServerCommand>();
        }

        // Time command retry
        stats.retry();

        retryQueue.offer(command);
    }

    public SessionServerCommand pollRetry() {
        SessionServerCommand command = null;

        if (retryQueue != null) {
            command = retryQueue.poll();
        }

        return command;
    }

    public boolean hasRetry() {
        return retryQueue != null && !retryQueue.isEmpty();
    }

    public void offerAbort(SessionServerAbort abort) {
        if (abortQueue == null) {
            abortQueue = new LinkedList<SessionServerAbort>();
        }

        // Time command abort
        stats.abort();

        abortQueue.offer(abort);
    }

    public SessionServerAbort pollAbort() {
        SessionServerAbort abort = null;

        if (abortQueue != null) {
            abort = abortQueue.poll();

            if (abort != null) {
                abort.setTaskMgmtStatus(taskMgmtStatus);
            }
        }

        return abort;
    }

    public boolean hasAbort() {
        return abortQueue != null && !abortQueue.isEmpty();
    }

    public TaskMgmtStatus getTaskMgmtStatus() {
        return taskMgmtStatus;
    }

    public void setTaskMgmtStatus(TaskMgmtStatus status) {
        this.taskMgmtStatus = status;
    }

    public SessionServerCommandStats getStats() {
        return stats;
    }

    @Override
    protected void createExchange() {
        SessionServerCommand command;

        if (primary != null) {
            logger.debugf("%s: command setup for retry", this);
            command = primary;
        } else {
            command = this;
        }

        CommandStatus status = command.getCommandStatus();

        CommandResponse response = new CommandResponse();

        response.setForeChannel(channel.isFore());

        response.setExchangeID(exchangeID);

        response.setSlotID(getSlotID());
        response.setSlotSN(getSlotSN());

        response.setStatus(status);

        if (status == CommandStatus.SUCCESS) {
            CommandRequest request = (CommandRequest) command.getRequest();
            SessionServerFuture future = command.getFuture();

            if (future.isForgotten()) {
                // Future could only be reset if the request is idempotent and the command instance is a retry
                assert request.isIdempotent();
                assert command != this;

                response.setStatus(CommandStatus.SLOT_UNCACHED);
            } else {
                try {
                    ServiceResponse svcResponse = future.get();
                    response.setResponse(svcResponse);
                } catch (ExecutionException e) {
                    ServiceException exception;
                    Throwable t = e.getCause();

                    logger.tracef(t, "%s: command failed", this);

                    /*
                     * Only service exception is sent over the protocol. If anything else is thrown from the service,
                     * we will transform it to a service exception first.
                     */
                    if (t instanceof ServiceExecutionException) {
                        exception = (ServiceExecutionException) t;
                    } else {
                        exception = new ServiceExecutionException(t);
                    }

                    response.setException(exception);
                } catch (CancellationException e) {
                    // This should never happen since cancelled command is never responded to
                    assert false;
                } catch (InterruptedException e) {
                    // This should never happen since the future is already done by now
                    assert false;
                }

                /*
                 * If the service request is idempotent, we are under no obligation to cache the service response. It
                 * is the responsibility of the application to retry an idempotent request in case of delivery failure.
                 * We will reset the future unconditionally even though we could defer dynamically based on resource
                 * consumption level.
                 */
                if (request.isIdempotent()) {
                    future.forget();
                }
            }
        }

        setResponse(response);
    }

    @Override
    public boolean send() {
        // Time command complete
        stats.complete();

        return super.send();
    }

    @Override
    public void receive() {
        // Time command start
        stats.start();

        channel.refresh(request);
        channel.getSibling().refresh(request);

        channel.exchangeReceived(this);
    }

    public void start() {
        // Time command (re)start
        stats.start();
    }
}
