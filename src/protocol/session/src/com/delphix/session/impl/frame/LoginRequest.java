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

/**
 * This class serves as the base for all login request frames.
 *
 * The login process has a maximum of four phases from when the transport connection is established until the
 * connection is ready for normal operation.
 *
 *  1) initial connection
 *
 * The initial connection is initiated by the client and involves a pair of messages, one in each direction. This
 * exchange does the following:
 *
 *   - negotiate the protocol version support
 *   - announce the client identity to the server
 *   - negotiate an optional TLS phase
 *   - announce the server supported SASL mechanisms to the client
 *
 *  2) TLS encryption (optional)
 *
 * The TLS encryption is done outside of the protocol. Upon completion of the TLS negotiation, the protocol shall be
 * notified and protocol login continues after that.
 *
 * In case both sides agree not to have TLS, this phase shall be skipped.
 *
 *  3) SASL authentication
 *
 * SASL is a mandatory phase in the protocol login process. Server announces the SASL mechanisms it supports in the
 * first login phase. Client chooses one of SASL mechanisms from the list to for authentication purpose.
 *
 * To skip authentication, the SASL mechanism "ANONYMOUS" may be used if both sides agree.
 *
 * SASL negotiation may involve multiple exchanges between client and server. The initial request from the client
 * should select the mechanism and include any optional data. The server is required to send a final outcome upon
 * completion of the SASL engine with optional data.
 *
 *  4) operational parameter negotiation
 *
 * After security negotiation is completed, the client and the server may have a final exchange for operational
 * parameter negotiation. It involves a pair of message initiated by the client.
 *
 * Here is an example that illustrates the exchanges during the login process.
 *
 *  client                              server
 *  ------                              ------
 *
 *    |          protocol version          |
 *    |          session handle = null     |
 *    |          client                    |
 *    |          service                   |
 *    |          ssl = true                |
 *    |      ------------------------>     |
 *    |                                    |
 *    |          ssl = true                |
 *    |          sasl mechs = <list>       |
 *    |      <------------------------     |
 *    |                                    |
 *    |        ssl negotiation start       |
 *    |      ========================>     |
 *    |      <========================     |
 *    |                ...                 |
 *    |        ssl negotiation end         |
 *    |      <========================     |
 *    |                                    |
 *    |       sasl mech = "SKEY"           |
 *    |       sasl initital response       |
 *    |      ------------------------>     |
 *    |                                    |
 *    |           sasl challenge           |
 *    |      <------------------------     |
 *    |           sasl response            |
 *    |      ------------------------>     |
 *    |                ...                 |
 *    |                                    |
 *    |           sasl outcome             |
 *    |      <------------------------     |
 *    |                                    |
 *    |         operational params         |
 *    |      ------------------------>     |
 *    |         operational params         |
 *    |      <------------------------     |
 *    |                                    |
 *    v                                    v
 *
 */
public abstract class LoginRequest extends RequestFrame {

    protected LoginRequest() {
        super();

        // Initialize the command sequences
        setCommandSN(SerialNumber.ZERO_SEQUENCE_INTEGER);
        setExpectedCommandSN(SerialNumber.ZERO_SEQUENCE_INTEGER);
    }

    @Override
    public boolean isForeChannel() {
        return true;
    }

    public abstract LoginPhase getPhase();
}
