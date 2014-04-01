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

package com.delphix.session.impl.frame;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * This class describes the login authenticate (a.k.a., SASL) response.
 *
 *   o challenge
 *
 * This is the SASL mechanism's challenge to the client.
 *
 *   o complete
 *
 * This field indicates whether the authentication process has been completed on the server side as determined by the
 * SASL mechanism. The client side SASL mechanism may or may not be completed at the same time. If not, it will be
 * after it processes the final challenge from the server.
 */
public class AuthenticateResponse extends LoginResponse {

    private byte[] challenge;
    private boolean complete;

    public AuthenticateResponse() {
        super();
    }

    @Override
    public LoginPhase getPhase() {
        return LoginPhase.AUTHENTICATE;
    }

    public byte[] getChallenge() {
        return challenge;
    }

    public void setChallenge(byte[] challenge) {
        this.challenge = challenge;
    }

    public boolean isComplete() {
        return complete;
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        if (status != LoginStatus.SUCCESS) {
            return;
        }

        int length = in.readInt();

        if (length > 0) {
            challenge = new byte[length];
            in.read(challenge);
        }

        complete = in.readBoolean();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        if (status != LoginStatus.SUCCESS) {
            return;
        }

        if (challenge != null) {
            out.writeInt(challenge.length);
            out.write(challenge);
        } else {
            out.writeInt(0);
        }

        out.writeBoolean(complete);
    }
}
