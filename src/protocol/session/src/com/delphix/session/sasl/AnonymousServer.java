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

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import java.util.HashMap;
import java.util.Map;

/**
 * The ANONYMOUS server mechanism requires no properties.
 *
 * The ANONYMOUS server mechanism requires no callbacks.
 */
public class AnonymousServer implements ServerSaslMechanism {

    private final Map<String, String> properties = new HashMap<String, String>();

    @Override
    public String getMechanism() {
        return SaslMechanism.ANONYMOUS;
    }

    @Override
    public Map<String, ?> getProperties() {
        return properties;
    }

    @Override
    public SaslServer create(String protocol, String server) throws SaslException {
        return Sasl.createSaslServer(getMechanism(), protocol, server, getProperties(), null);
    }
}
