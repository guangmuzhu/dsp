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

package com.delphix.session.impl.channel.server;

import com.delphix.session.impl.frame.RequestFrame;
import com.delphix.session.impl.frame.TaskMgmtRequest;
import com.delphix.session.impl.frame.TaskMgmtResponse;
import com.delphix.session.impl.frame.TaskMgmtStatus;

public class SessionServerAbort extends SessionServerExchange {

    private SessionServerCommand command; // Server command
    private TaskMgmtStatus taskMgmtStatus; // Task management status

    public SessionServerAbort(SessionServerChannel channel, RequestFrame request) {
        super(channel, request);
    }

    public SessionServerCommand getCommand() {
        return command;
    }

    public void setCommand(SessionServerCommand command) {
        this.command = command;

        // Set the exchange dependency for the sake of transport integrity
        setDependency(command);
    }

    public TaskMgmtStatus getTaskMgmtStatus() {
        return taskMgmtStatus;
    }

    public void setTaskMgmtStatus(TaskMgmtStatus status) {
        this.taskMgmtStatus = status;
    }

    @Override
    protected void createExchange() {
        TaskMgmtResponse response = new TaskMgmtResponse();

        response.setForeChannel(channel.isFore());

        response.setExchangeID(exchangeID);
        response.setStatus(taskMgmtStatus);

        setResponse(response);
    }

    @Override
    public void receive() {
        channel.refresh(request);
        channel.getSibling().refresh(request);

        channel.exchangeReceived(this);
    }

    @Override
    public String toString() {
        TaskMgmtRequest request = (TaskMgmtRequest) this.request;
        String target = request.getTargetExchangeID().toString();

        return super.toString() + "{" + target + "}";
    }
}
