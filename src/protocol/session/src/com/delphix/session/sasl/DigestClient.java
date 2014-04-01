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
import javax.security.sasl.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * The DIGEST-MD5 client mechanism supports the following properties.
 *
 *  - Sasl.QOP
 *  - Sasl.STRENGTH
 *  - Sasl.MAX_BUFFER
 *  - Sasl.SERVER_AUTH
 *  - "javax.security.sasl.sendmaxbuffer"
 *  - "com.sun.security.sasl.digest.cipher"
 *
 * We are using DIGEST-MD5 for authentication only as opposed to as a security layer. The only properties applicable
 * to authentication only mode are QOP and SERVER_AUTH.
 *
 * The DIGEST-MD5 client mechanism requires the following callbacks.
 *
 *  - RealmCallback
 *  - RealmChoiceCallback
 *  - NameCallback
 *  - PasswordCallback
 *
 * The "com.sun.security.sasl.digest.realm" property is used to specify a list of space-separated realm names that
 * the server supports. The list is sent to the client as part of the challenge. If this property has not been set,
 * the default realm is the server's name (supplied as a parameter).
 */
public class DigestClient implements ClientSaslMechanism {

    private final Map<String, String> properties = new HashMap<String, String>();

    private final String username;
    private final String password;
    private final String realm;

    public DigestClient(String username, String password) {
        this(username, password, null);
    }

    public DigestClient(String username, String password, String realm) {
        this.username = username;
        this.password = password;
        this.realm = realm;

        // Set the QOP to authentication only
        properties.put(Sasl.QOP, "auth");

        // Set the server authentication to false to request for client authentication only
        properties.put(Sasl.SERVER_AUTH, "false");
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
    public SaslClient create(String protocol, String server, String authzid) throws SaslException {
        String[] mechanisms = { getMechanism() };
        CallbackHandler handler = new DigestClientHandler();

        return Sasl.createSaslClient(mechanisms, authzid, protocol, server, getProperties(), handler);
    }

    private class DigestClientHandler implements CallbackHandler {

        @Override
        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback) {
                    NameCallback nc = (NameCallback) callback;
                    nc.setName(username);
                } else if (callback instanceof PasswordCallback) {
                    PasswordCallback pc = (PasswordCallback) callback;
                    pc.setPassword(password.toCharArray());
                } else if (callback instanceof RealmCallback) {
                    RealmCallback rc = (RealmCallback) callback;

                    /*
                     * This callback is made if the "com.sun.security.sasl.digest.realm" property is not used.
                     *
                     * If a realm has been specified in the callback handler, use it; otherwise use the default realm
                     * which is the server's name supplied as a parameter to Sasl.createSaslServer.
                     */
                    if (realm != null) {
                        rc.setText(realm);
                    } else {
                        rc.setText(rc.getDefaultText());
                    }
                } else if (callback instanceof RealmChoiceCallback) {
                    RealmChoiceCallback rc = (RealmChoiceCallback) callback;

                    /*
                     * This callback is made if the "com.sun.security.sasl.digest.realm" property is used to specify a
                     * list of space-separated realm names that the server supports. The realm list is communicated to
                     * the client via a set of realm directives in the digest-challenge.
                     *
                     * If a realm has been specified by the callback handler, try to locate it in the server's realm list
                     * first. Use it if found; otherwise fail the authentication. If a realm is not specified, use the
                     * default realm from the server's realm list.
                     */
                    if (realm != null) {
                        String[] choices = rc.getChoices();
                        boolean found = false;

                        // Select the realm from the list
                        for (int i = 0; i < choices.length; i++) {
                            if (choices[i].equalsIgnoreCase(realm)) {
                                rc.setSelectedIndex(i);
                                found = true;
                                break;
                            }
                        }

                        if (!found) {
                            throw new IOException("realm " + realm + " not available");
                        }
                    } else {
                        rc.setSelectedIndex(rc.getDefaultChoice());
                    }
                } else {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        }
    }
}
