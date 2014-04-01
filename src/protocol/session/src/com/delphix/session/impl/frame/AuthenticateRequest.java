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
 * This class describes the login authenticate (a.k.a., SASL) request.
 *
 *   o initial
 *
 * This indicates whether it is the first authenticate request in the sequence.
 *
 *   o mechanism
 *
 * This is the SASL mechanism selected by the client from the list of mechanisms supported by the server. This should
 * only be set in the first authenticate request in the sequence. If set in subsequent requests, the value must match
 * the one already selected.
 *
 *   o response
 *
 * This is the SASL mechanism's response to a server challenge. For the first authenticate request, this may either be
 * the empty response or an initial response, depending on the SASL mechanism in use.
 */
public class AuthenticateRequest extends LoginRequest {

    private boolean initial;
    private String mechanism;
    private byte[] response;

    public AuthenticateRequest() {
        super();
    }

    @Override
    public LoginPhase getPhase() {
        return LoginPhase.AUTHENTICATE;
    }

    public boolean isInitial() {
        return initial;
    }

    public void setInitial(boolean initial) {
        this.initial = initial;
    }

    public String getMechanism() {
        return mechanism;
    }

    public void setMechanism(String mechanism) {
        this.mechanism = mechanism;
    }

    public byte[] getResponse() {
        return response;
    }

    public void setResponse(byte[] response) {
        this.response = response;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        initial = in.readBoolean();

        mechanism = in.readUTF();

        response = new byte[in.readInt()];
        in.read(response);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeBoolean(initial);

        out.writeUTF(mechanism);

        out.writeInt(response.length);
        out.write(response);
    }
}
