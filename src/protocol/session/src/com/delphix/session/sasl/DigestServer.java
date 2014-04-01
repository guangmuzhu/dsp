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

import org.apache.commons.lang.StringUtils;

import javax.security.auth.callback.*;
import javax.security.sasl.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * The DIGEST-MD5 server mechanism supports the following properties.
 *
 *  - Sasl.QOP
 *  - Sasl.STRENGTH
 *  - Sasl.MAX_BUFFER
 *  - "javax.security.sasl.sendmaxbuffer"
 *  - "com.sun.security.sasl.digest.realm"
 *  - "com.sun.security.sasl.digest.utf8"
 *
 * We are using DIGEST-MD5 for authentication only as opposed to as a security layer. The only properties applicable
 * to authentication only mode are QOP, utf8, and realm.
 *
 * The DIGEST-MD5 server mechanism requires the following callbacks.
 *
 *  - RealmCallback
 *  - NameCallback
 *  - PasswordCallback
 *  - AuthorizeCallback
 *
 * The Digest-MD5 server mechanism makes use of the RealmCallback, NameCallback, and PasswordCallback in the same
 * order to obtain the password required to verify the SASL client's response. The callback handler should use
 * RealmCallback.getDefaultText() and NameCallback.getDefaultName() as keys to fetch the password.
 */
public class DigestServer implements ServerSaslMechanism {

    // Mechanism configuration properties
    private static String REALM_PROPERTY = "com.sun.security.sasl.digest.realm";
    private static String UTF8_PROPERTY = "com.sun.security.sasl.digest.utf8";

    private final Map<String, String> properties = new HashMap<String, String>();
    private final PasswordStore passwd;
    private final UserMapper mapper;

    public DigestServer(PasswordStore passwd) {
        this(passwd, null);
    }

    public DigestServer(PasswordStore passwd, UserMapper mapper) {
        this(passwd, mapper, null);
    }

    public DigestServer(PasswordStore passwd, UserMapper mapper, String[] realms) {
        this.passwd = passwd;
        this.mapper = mapper;

        // Set the QOP for authentication only
        properties.put(Sasl.QOP, "auth");

        // Set the character encoding to utf-8
        properties.put(UTF8_PROPERTY, "true");

        /*
         * Construct a whitespace delimited string of realms for the DIGEST-MD5 realm property. The value will be
         * passed to the client during the initial SASL challenge. If not set, the authentication will fall back to
         * use the server name as the realm.
         */
        if (realms != null && realms.length > 0) {
            properties.put(REALM_PROPERTY, StringUtils.join(realms, " "));
        }
    }

    @Override
    public String getMechanism() {
        return SaslMechanism.DIGEST_MD5;
    }

    @Override
    public Map<String, ?> getProperties() {
        return properties;
    }

    @Override
    public SaslServer create(String protocol, String server) throws SaslException {
        CallbackHandler handler = new DigestServerHandler();
        return Sasl.createSaslServer(getMechanism(), protocol, server, getProperties(), handler);
    }

    private class DigestServerHandler implements CallbackHandler {

        private String realm;
        private String username;

        @Override
        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (Callback callback : callbacks) {
                if (callback instanceof RealmCallback) {
                    RealmCallback rc = (RealmCallback) callback;

                    /*
                     * This is the first in a series of callbacks made by the Digest-MD5 mechanism. The realm info is
                     * specified optionally in the digest-response and made available via the default text of the realm
                     * callback.
                     */
                    realm = rc.getDefaultText();
                    rc.setText(realm);
                } else if (callback instanceof NameCallback) {
                    NameCallback nc = (NameCallback) callback;

                    /*
                     * This is the second in a series of callbacks made by the Digest-MD5 mechanism. The username is
                     * specified in the digest-response and made available via the default text of the name callback.
                     */
                    username = nc.getDefaultName();
                    nc.setName(username);
                } else if (callback instanceof PasswordCallback) {
                    PasswordCallback pc = (PasswordCallback) callback;

                    /*
                     * This is the third in a series of callbacks made by the Digest-MD5 mechanism. The password is
                     * retrieved for the realm/user from the user database and used for authentication by the mechanism.
                     */
                    String password = passwd.getPassword(realm, username);

                    if (password != null) {
                        pc.setPassword(password.toCharArray());
                    } else {
                        throw new IOException("user " + realm + "/" + username + " doesn't exist");
                    }
                } else if (callback instanceof AuthorizeCallback) {
                    AuthorizeCallback ac = (AuthorizeCallback) callback;

                    /*
                     * This is the last callback made by the Digest-MD5 mechanism after authentication is completed. It
                     * checks to see if the user identified by the authentication ID may continue as one identified by
                     * the authorization ID.
                     */
                    String authzid;

                    if (mapper != null) {
                        authzid = mapper.authorize(realm, ac.getAuthenticationID(), ac.getAuthorizationID());
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
