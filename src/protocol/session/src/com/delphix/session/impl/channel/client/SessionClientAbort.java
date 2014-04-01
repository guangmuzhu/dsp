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

import com.delphix.session.impl.frame.SerialNumber;
import com.delphix.session.impl.frame.TaskMgmtRequest;
import com.delphix.session.impl.frame.TaskMgmtResponse;

public class SessionClientAbort extends SessionClientExchange {

    private final SessionClientCommand command; // Target command

    private final int targetSlotID; // Target slot ID
    private final SerialNumber targetSlotSN; // Target slot SN

    public SessionClientAbort(SessionClientChannel channel, SessionClientCommand command) {
        super(channel);

        this.command = command;

        /*
         * The command may very well be on its way to complete while the abort is being set up. We will remember the
         * target slot ID and slot SN rather than the slot reference itself just in case the slot is released by the
         * target command and reassigned to another command.
         */
        SessionClientSlot slot = command.getSlot();

        this.targetSlotID = slot.getSlotID();
        this.targetSlotSN = slot.getSlotSN();

        // Set the exchange dependency for the sake of transport integrity
        setDependency(command);

        // Set the abort on the target command
        command.setAbort(this);
    }

    public SessionClientCommand getTargetCommand() {
        return command;
    }

    @Override
    protected void createExchange() {
        TaskMgmtRequest request = new TaskMgmtRequest();

        request.setForeChannel(channel.isFore());

        request.setExchangeID();

        request.setTargetCommandSN(command.getCommandSN());
        request.setTargetExchangeID(command.getExchangeID());

        request.setTargetSlotID(targetSlotID);
        request.setTargetSlotSN(targetSlotSN);

        setRequest(request);
    }

    @Override
    public void receive() {
        if (!(response instanceof TaskMgmtResponse)) {
            handleInvalidResponse();
            return;
        }

        channel.refresh(response);
        channel.getSibling().refresh(response);

        // Send the abort to the channel for completion
        channel.exchangeDone(this);
    }

    @Override
    public void reset() {
        logger.errorf("%s: failed with %s over %s", this, status, xport);

        // Send the abort to the channel for reset
        channel.exchangeDone(this);
    }

    @Override
    public String toString() {
        return super.toString() + "{" + command + "}";
    }
}
