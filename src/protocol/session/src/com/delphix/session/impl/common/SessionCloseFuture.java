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

package com.delphix.session.impl.common;

import com.delphix.session.service.CloseFuture;
import com.delphix.session.service.ServiceNexus;
import com.delphix.session.util.AbstractFuture;

public class SessionCloseFuture extends AbstractFuture<ServiceNexus> implements CloseFuture {

    private final SessionNexus nexus;
    private final Throwable cause;

    public SessionCloseFuture(SessionNexus nexus) {
        this(nexus, null);
    }

    public SessionCloseFuture(SessionNexus nexus, Throwable cause) {
        this.nexus = nexus;
        this.cause = cause;
    }

    @Override
    protected void doCancel(boolean mayInterruptIfRunning) {
        // Cancellation is ignored - close must be completed regardless
    }

    @Override
    protected void doRun() {
        nexus.stop();
    }

    @Override
    public ServiceNexus getNexus() {
        return nexus;
    }

    @Override
    public Throwable getCause() {
        return cause;
    }
}
