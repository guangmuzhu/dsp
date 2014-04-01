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

import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

/**
 * This interfaces describes the server side of a SASL mechanism.
 *
 * A protocol server using SASL for authentication should create an instance of the SASL mechanism class that
 * implements this interface for each mechanism that it supports. The client chooses one of the SASL mechanisms to
 * use during the initial protocol handshake. From the server mechanism chosen, one may create an authentication
 * context used for SASL challenge generation and response evaluation.
 */
public interface ServerSaslMechanism extends SaslMechanism {

    /**
     * Create a SASL server authentication context with the specified protocol and server name.
     */
    public SaslServer create(String protocol, String server) throws SaslException;
}
