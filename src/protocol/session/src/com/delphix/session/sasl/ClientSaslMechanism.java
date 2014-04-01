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

import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

/**
 * This interface describes the client side of a SASL mechanism.
 *
 * A protocol client using SASL for authentication should select a SASL mechanism from the list of mechanisms
 * advertised by the server and create an instance of the corresponding SASL mechanism class that implements this
 * interface. From the client mechanism chosen, one may create an authentication context used for SASL challenge
 * evaluation and response generation.
 */
public interface ClientSaslMechanism extends SaslMechanism {

    /**
     * Create a SASL client authentication context with the specified protocol, server name, and optionally
     * authorization ID.
     */
    public SaslClient create(String protocol, String server, String authzid) throws SaslException;
}
