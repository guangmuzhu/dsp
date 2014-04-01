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

package com.delphix.session.sasl;

import javax.security.auth.callback.*;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * The PLAIN client mechanism requires no properties.
 *
 * The PLAIN client mechanism requires the following callbacks.
 *
 *  - NameCallback
 *  - PasswordCallback
 */
public class PlainClient implements ClientSaslMechanism {

    private final Map<String, String> properties = new HashMap<String, String>();

    private final String username;
    private final String password;

    public PlainClient(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public String getMechanism() {
        return SaslMechanism.PLAIN;
    }

    @Override
    public Map<String, ?> getProperties() {
        return properties;
    }

    @Override
    public SaslClient create(String protocol, String server, String authzid) throws SaslException {
        String[] mechanisms = { getMechanism() };
        CallbackHandler handler = new PlainClientHandler();

        return Sasl.createSaslClient(mechanisms, authzid, protocol, server, getProperties(), handler);
    }

    private class PlainClientHandler implements CallbackHandler {

        @Override
        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback) {
                    NameCallback nc = (NameCallback) callback;
                    nc.setName(username);
                } else if (callback instanceof PasswordCallback) {
                    PasswordCallback pc = (PasswordCallback) callback;
                    pc.setPassword(password.toCharArray());
                } else {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        }
    }
}
