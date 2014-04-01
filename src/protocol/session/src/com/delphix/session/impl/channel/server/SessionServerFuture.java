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

import com.delphix.session.service.ServiceFuture;
import com.delphix.session.service.ServiceRequest;
import com.delphix.session.service.ServiceResponse;
import com.delphix.session.util.ThreadFuture;

import java.util.concurrent.Callable;

public class SessionServerFuture extends ThreadFuture<ServiceResponse> implements ServiceFuture {

    protected final SessionServerCommand command;
    protected boolean forgotten;

    public SessionServerFuture(SessionServerCommand command, Callable<ServiceResponse> callable) {
        super(callable);

        this.command = command;
    }

    public SessionServerCommand getCommand() {
        return command;
    }

    public synchronized void forget() {
        assert taskDone();

        if (!forgotten) {
            result = null;
            exception = null;
            forgotten = true;
        }
    }

    public synchronized boolean isForgotten() {
        return forgotten;
    }

    @Override
    protected void beforeRun() throws InterruptedException {
        super.beforeRun();

        // Record the process timestamp
        command.getStats().process();
    }

    @Override
    protected void afterRun() {
        super.afterRun();

        /*
         * Release the callable upon completion. That will lead to the release of the service request held by it.
         * The service request is not needed any more since we will never execute the same service request again.
         */
        callable = null;
    }

    @Override
    public ServiceRequest getServiceRequest() {
        return command.getServiceRequest();
    }
}
