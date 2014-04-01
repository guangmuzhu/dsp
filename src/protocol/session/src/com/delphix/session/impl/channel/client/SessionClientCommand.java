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

package com.delphix.session.impl.channel.client;

import com.delphix.session.impl.common.SessionNexus;
import com.delphix.session.impl.frame.CommandRequest;
import com.delphix.session.impl.frame.CommandResponse;
import com.delphix.session.impl.frame.SerialNumber;
import com.delphix.session.service.ServiceFuture;
import com.delphix.session.service.ServiceRequest;
import com.delphix.session.util.ByteBufferUtil;
import com.delphix.session.util.TaskMgmtSync;

import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledFuture;

public class SessionClientCommand extends SessionClientExchange {

    private final ServiceRequest serviceRequest; // Service command
    private final SessionClientFuture future; // Command future

    private SessionClientCommandState state; // Command state
    private boolean pending; // Initial dispatch pending
    private boolean proceed; // Initial dispatch proceed

    private SessionClientSlot slot; // Command slot
    private SerialNumber commandSN; // Command sequence

    private final TaskMgmtSync taskMgmt; // Task management state
    private SessionClientAbort abort; // Task management exchange

    private ScheduledFuture<?> timeout; // Command timeout

    private final SessionClientCommandStats stats; // Command stats

    private boolean hasTokens; // Command has already consumed the required tokens

    public SessionClientCommand(SessionClientChannel channel, ServiceRequest request) {
        this(channel, request, null);
    }

    public SessionClientCommand(SessionClientChannel channel, ServiceRequest request, final Runnable done) {
        super(channel);

        this.serviceRequest = request;

        future = new SessionClientFuture(this) {

            @Override
            protected void done() {
                if (done != null) {
                    done.run();
                }
            }
        };

        taskMgmt = new TaskMgmtSync();

        // Initialize the command state
        setState(SessionClientCommandState.INITIAL);

        stats = new SessionClientCommandStats();
    }

    public ServiceRequest getServiceRequest() {
        return serviceRequest;
    }

    public ServiceFuture getFuture() {
        return future;
    }

    public SessionClientCommandState getState() {
        return state;
    }

    public void setState(SessionClientCommandState newState) {
        SessionClientCommandState oldState = state;

        SessionClientCommandState.validate(oldState, newState);
        state = newState;

        // Set the initial dispatch pending to true if entering PENDING state; otherwise false
        if (newState == SessionClientCommandState.PENDING) {
            blockDispatch();
        } else if (oldState == SessionClientCommandState.PENDING) {
            if (newState == SessionClientCommandState.ACTIVE) {
                notifyDispatch(true);
            } else {
                notifyDispatch(false);
            }
        }

        logger.tracef("%s: state transition %s -> %s", this, oldState, newState);
    }

    public SessionClientSlot getSlot() {
        return slot;
    }

    public void setSlot(SessionClientSlot slot) {
        this.slot = slot;
    }

    public void resetSlot() {
        setSlot(null);
    }

    public SerialNumber getCommandSN() {
        return commandSN;
    }

    public void setCommandSN(SerialNumber commandSN) {
        this.commandSN = commandSN;
    }

    public SessionClientAbort getAbort() {
        return abort;
    }

    public void setAbort(SessionClientAbort abort) {
        this.abort = abort;
    }

    public void resetAbort() {
        setAbort(null);
    }

    public SessionClientCommandStats getStats() {
        return stats;
    }

    @Override
    protected void createExchange() {
        CommandRequest request = new CommandRequest();

        request.setForeChannel(channel.isFore());

        request.setExchangeID();
        request.setCommandSN(commandSN);

        request.setSlotID(slot.getSlotID());
        request.setSlotSN(slot.getSlotSN());

        request.setRequest(serviceRequest);
        request.setIdempotent(serviceRequest.isIdempotent());

        setRequest(request);
    }

    @Override
    public boolean send() {
        // Time command send
        stats.send();

        if (!super.send()) {
            // Time command receive (fake really)
            stats.receive();
            return false;
        }

        return true;
    }

    @Override
    public void receive() {
        if (!(response instanceof CommandResponse)) {
            handleInvalidResponse();
            return;
        }

        // Time command receive
        stats.receive();

        channel.refresh(response);
        channel.getSibling().refresh(response);

        // Send the command to the channel for completion
        channel.exchangeDone(this);
    }

    @Override
    public void reset() {
        logger.errorf("%s: failed with %s over %s", this, status, xport);

        // Time command reset
        stats.reset();

        // Send the command to the channel for reset
        channel.exchangeDone(this);
    }

    public void scheduleTimeout(long delay) {
        if (delay == 0) {
            return;
        }

        Runnable task = new Runnable() {

            @Override
            public void run() {
                commandTimeout();
            }
        };

        timeout = channel.getNexus().schedule(task, delay);
    }

    private void commandTimeout() {
        logger.errorf("%s: command timed out", this);
        channel.abort(this);
    }

    public boolean disableTaskMgmt() {
        return taskMgmt.block();
    }

    public void enableTaskMgmt() {
        taskMgmt.unblock();
    }

    public void startTaskMgmt() {
        // Time command abort
        stats.abort();

        try {
            taskMgmt.start();
        } catch (InterruptedException e) {
            logger.errorf("%s: task management interrupted", this);
        }
    }

    public boolean start() {
        // Time command start
        stats.start();

        return disableTaskMgmt();
    }

    private void updateDataStats() {
        CommandRequest cmd = (CommandRequest) request;

        // If the command is aborted before the wire exchange has been setup cmd will be null
        if (cmd != null) {
            long dataSize = getDataSize();

            stats.setDataSize(dataSize);

            if (cmd.getCompressedDataSize() == 0) {
                stats.setCompressedDataSize(dataSize);
            } else {
                stats.setCompressedDataSize(cmd.getCompressedDataSize());
            }
        }
    }

    public void complete(Throwable t) {
        CommandResponse response = (CommandResponse) this.response;

        // Cancel the timeout if necessary
        if (timeout != null) {
            timeout.cancel(false);
        }

        final Throwable throwable;

        if (t == null) {
            throwable = response.getException();
        } else {
            throwable = t;
        }

        // Update the command data stats
        updateDataStats();

        /*
         * In case the command has to be completed due to abort while the channel is down, we have to keep enough
         * state about the command in order to re-synchronize with the server later when the channel is restored.
         * But we should release the service command reference now that it is completed.
         */
        request = null;

        // Time command complete
        stats.complete();

        // Execute the command completion callback in a separate context
        SessionNexus nexus = channel.getNexus();

        nexus.getExecutor().execute(new Runnable() {

            @Override
            public void run() {
                notifyComplete(throwable);
            }
        });
    }

    private void notifyComplete(Throwable t) {
        CommandResponse response = (CommandResponse) this.response;

        // Set the result or exception in the future which will invoke the done callback if any
        if (t != null) {
            future.setException(t);
        } else {
            future.setResult(response.getResponse());
        }
    }

    public synchronized boolean syncDispatch() throws InterruptedException {
        while (pending) {
            wait();
        }

        return proceed;
    }

    private synchronized void blockDispatch() {
        pending = true;
    }

    private synchronized void notifyDispatch(boolean active) {
        proceed = active;
        pending = false;

        notify();
    }

    public long getDataSize() {
        ByteBuffer[] data = serviceRequest.getData();

        if (data == null) {
            return 0;
        }

        return ByteBufferUtil.remaining(data);
    }

    public void setHasTokens() {
        hasTokens = true;
    }

    public boolean hasTokens() {
        return hasTokens;
    }

    public void setThrottled() {
        stats.setThrottled();
    }
}
