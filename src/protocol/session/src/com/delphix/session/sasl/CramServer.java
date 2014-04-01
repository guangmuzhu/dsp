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
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * The CRAM-MD5 server mechanism requires no properties.
 *
 * The CRAM-MD5 server mechanism requires the following callbacks.
 *
 *  - NameCallback
 *  - PasswordCallback
 *  - AuthorizeCallback
 */
public class CramServer implements ServerSaslMechanism {

    private final Map<String, String> properties = new HashMap<String, String>();
    private final PasswordStore passwd;
    private final UserMapper mapper;

    public CramServer(PasswordStore passwd) {
        this(passwd, null);
    }

    public CramServer(PasswordStore passwd, UserMapper mapper) {
        this.passwd = passwd;
        this.mapper = mapper;
    }

    @Override
    public String getMechanism() {
        return SaslMechanism.CRAM_MD5;
    }

    @Override
    public Map<String, ?> getProperties() {
        return properties;
    }

    @Override
    public SaslServer create(String protocol, String server) throws SaslException {
        CallbackHandler handler = new CramServerHandler();
        return Sasl.createSaslServer(getMechanism(), protocol, server, getProperties(), handler);
    }

    private class CramServerHandler implements CallbackHandler {

        private String username;

        @Override
        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback) {
                    NameCallback nc = (NameCallback) callback;
                    username = nc.getDefaultName();
                    nc.setName(username);
                } else if (callback instanceof PasswordCallback) {
                    PasswordCallback pc = (PasswordCallback) callback;

                    String password = passwd.getPassword(username);

                    if (password != null) {
                        pc.setPassword(password.toCharArray());
                    } else {
                        throw new IOException("user " + username + " doesn't exist");
                    }
                } else if (callback instanceof AuthorizeCallback) {
                    AuthorizeCallback ac = (AuthorizeCallback) callback;

                    String authzid;

                    if (mapper != null) {
                        authzid = mapper.authorize(ac.getAuthenticationID(), ac.getAuthorizationID());
                    } else {
                        authzid = ac.getAuthorizationID();
                    }

                    if (authzid != null) {
                        ac.setAuthorized(true);

                        /*
                         * Set the authorized ID only if it is different from the authorization ID. The authorized ID
                         * returned from the user mapper above might be canonicalized for the environment in which it
                         * will be used. We need to tell the SASL mechanism if that is the case.
                         */
                        if (!authzid.equals(ac.getAuthorizationID())) {
                            ac.setAuthorizedID(authzid);
                        }
                    } else {
                        ac.setAuthorized(false);
                    }
                } else {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        }
    }
}
