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

package com.delphix.session.impl.sasl;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

public abstract class AbstractSaslServer extends AbstractSaslBase implements SaslServer {

    protected AbstractSaslServer(String mechanism, String protocol, String serverName, CallbackHandler cbh) {
        super(mechanism, protocol, serverName, cbh);
    }

    @Override
    public byte[] evaluateResponse(byte[] response) throws SaslException {
        return process(response);
    }

    @Override
    public String getAuthorizationID() {
        if (!isComplete()) {
            throw new IllegalStateException("sasl authentication not complete");
        }

        return authorizationId;
    }
}
