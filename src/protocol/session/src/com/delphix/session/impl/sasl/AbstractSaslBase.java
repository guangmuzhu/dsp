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

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * This abstract class serves as the base for a concrete implementation of the SaslClient or SaslServer interface.
 * It provides default implementations for some of the methods.
 */
public abstract class AbstractSaslBase {

    protected final String mechanism;
    protected final String protocol;
    protected final String serverName;
    protected final CallbackHandler cbh;

    protected String authorizationId;

    protected enum SaslState {
        INITIAL,
        SUCCESS,
        FAILURE
    };

    protected SaslState state;

    protected AbstractSaslBase(String mechanism, String protocol, String serverName, CallbackHandler cbh) {
        this(mechanism, protocol, serverName, null, cbh);
    }

    protected AbstractSaslBase(String mechanism, String protocol, String serverName,
            String authorizationId, CallbackHandler cbh) {
        this.mechanism = mechanism;
        this.protocol = protocol;
        this.serverName = serverName;
        this.authorizationId = authorizationId;
        this.cbh = cbh;

        state = SaslState.INITIAL;
    }

    public String getMechanismName() {
        return mechanism;
    }

    public Object getNegotiatedProperty(String propName) {
        if (!isComplete()) {
            throw new IllegalStateException("sasl authentication not complete");
        }

        /*
         * The Sasl.QOP property refers to the quality of protection negotiated for use, which may have one of the
         * following literals as its value.
         *
         *   "auth" - authentication only
         *   "auth-int" - authentication plus integrity protection
         *   "auth-conf" - authentication plus integrity and confidentiality protection
         *
         * By default, set it to "auth" and override this method for SASL mechanism providing higher QOP.
         */
        if (propName.equals(Sasl.QOP)) {
            return "auth";
        } else {
            return null;
        }
    }

    public boolean isComplete() {
        return state == SaslState.SUCCESS;
    }

    protected void setComplete() {
        state = SaslState.SUCCESS;
    }

    public byte[] unwrap(byte[] incoming, int offset, int len) throws SaslException {
        if (!isComplete()) {
            throw new IllegalStateException("sasl authentication not complete");
        }

        throw new SaslException("sasl qop authentication only");
    }

    public byte[] wrap(byte[] outgoing, int offset, int len) throws SaslException {
        if (!isComplete()) {
            throw new IllegalStateException("sasl authentication not complete");
        }

        throw new SaslException("sasl qop authentication only");
    }

    public void dispose() throws SaslException {
        // Do nothing
    }

    protected byte[] process(byte[] incoming) throws SaslException {
        byte[] outgoing;

        switch (state) {
        case INITIAL:
            try {
                outgoing = evaluate(incoming);
            } catch (SaslException e) {
                state = SaslState.FAILURE;
                throw e;
            }

            break;

        case SUCCESS:
            throw new IllegalStateException("sasl authentication already complete");

        case FAILURE:
            throw new IllegalStateException("sasl authentication failed previously");

        default:
            throw new IllegalStateException("sasl state invalid");
        }

        return outgoing;
    }

    protected abstract byte[] evaluate(byte[] message) throws SaslException;

    protected void invokeCallbacks(Callback... callbacks) throws SaslException {
        try {
            cbh.handle(callbacks);
        } catch (IOException e) {
            throw new SaslException("failed to invoke callback", e);
        } catch (UnsupportedCallbackException e) {
            throw new SaslException("unsupported callback " + e.getCallback().getClass(), e);
        }
    }

    protected String fromUTF(byte[] message) throws SaslException {
        try {
            return new String(message, "UTF8");
        } catch (UnsupportedEncodingException e) {
            throw new SaslException("utf-8 charset not supported", e);
        }
    }

    protected byte[] toUTF(String string) throws SaslException {
        try {
            return string.getBytes("UTF8");
        } catch (UnsupportedEncodingException e) {
            throw new SaslException("utf-8 charset not supported", e);
        }
    }
}
