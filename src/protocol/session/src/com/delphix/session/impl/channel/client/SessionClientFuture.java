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

import com.delphix.session.service.DispatchInterruptedException;
import com.delphix.session.service.ServiceFuture;
import com.delphix.session.service.ServiceRequest;
import com.delphix.session.service.ServiceResponse;
import com.delphix.session.util.AbstractFuture;

public class SessionClientFuture extends AbstractFuture<ServiceResponse> implements ServiceFuture {

    private final SessionClientCommand command;

    public SessionClientFuture(SessionClientCommand command) {
        this.command = command;
    }

    @Override
    protected void doCancel(boolean mayInterruptIfRunning) {
        command.getChannel().abort(command);
    }

    @Override
    protected void work() {
        try {
            doRun();
        } catch (DispatchInterruptedException e) {
            /*
             * Although we are interrupted during dispatch, the command has already been entered into the channel.
             * The channel ensures that each and every command successfully registered with it will get completed
             * at some point. So we don't want to complete the command here as we do for any other exceptions.
             */
            throw e;
        } catch (Throwable t) {
            setException(t);
        }
    }

    @Override
    protected void doRun() throws Exception {
        command.getChannel().dispatch(command);
    }

    public SessionClientCommand getCommand() {
        return command;
    }

    @Override
    public ServiceRequest getServiceRequest() {
        return command.getServiceRequest();
    }
}
