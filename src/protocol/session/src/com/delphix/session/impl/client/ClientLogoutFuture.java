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

package com.delphix.session.impl.client;

import com.delphix.session.service.ClientNexus;
import com.delphix.session.util.AbstractFuture;

public class ClientLogoutFuture extends AbstractFuture<ClientNexus> {

    private final ClientSession session;

    public ClientLogoutFuture(ClientSession session) {
        this.session = session;
    }

    @Override
    protected void doCancel(boolean mayInterruptIfRunning) {
        // Cancellation is ignored - logout is guaranteed to complete within the negotiated timeout
    }

    @Override
    protected void doRun() throws Exception {
        session.logout();
    }

    @Override
    protected void done() {
        session.logoutComplete();
    }

    public ClientNexus getNexus() {
        return session;
    }
}
