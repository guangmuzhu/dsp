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

import javax.security.sasl.Sasl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractSaslFactory {

    protected static final Map<String, String> DEFAULT; // Default policies
    protected static final String[] UNSUPPORTED; // Unsupported mechanisms

    protected final String mechanism;
    protected final String[] mechanisms;
    protected final Map<String, String> policies;

    static {
        DEFAULT = new HashMap<String, String>();

        DEFAULT.put(Sasl.POLICY_NOANONYMOUS, Boolean.FALSE.toString());
        DEFAULT.put(Sasl.POLICY_NOPLAINTEXT, Boolean.FALSE.toString());
        DEFAULT.put(Sasl.POLICY_NODICTIONARY, Boolean.FALSE.toString());
        DEFAULT.put(Sasl.POLICY_NOACTIVE, Boolean.FALSE.toString());
        DEFAULT.put(Sasl.POLICY_FORWARD_SECRECY, Boolean.FALSE.toString());
        DEFAULT.put(Sasl.POLICY_PASS_CREDENTIALS, Boolean.FALSE.toString());

        UNSUPPORTED = new String[0];
    }

    protected AbstractSaslFactory(String mechanism) {
        this.mechanism = mechanism;

        mechanisms = new String[] { mechanism };

        policies = new HashMap<String, String>();
        policies.putAll(DEFAULT);
    }

    public String[] getMechanismNames(Map<String, ?> props) {
        return filter(props) ? mechanisms : UNSUPPORTED;
    }

    protected boolean filter(String mechanism, Map<String, ?> props) {
        return mechanism.equals(this.mechanism) && filter(props);
    }

    protected boolean filter(String[] mechanisms, Map<String, ?> props) {
        return Arrays.asList(mechanisms).contains(mechanism) && filter(props);
    }

    /**
     * Check if the SASL factory implements mechanisms satisfying the desired properties. Filter rules are described
     * as follows.
     *
     *  - policies not included in props assume default values
     *  - policies with false values always result in success
     *  - null props always result in success
     *  - non-policy related properties, if present in props, are ignored (per java doc)
     */
    protected boolean filter(Map<String, ?> props) {
        if (props == null) {
            return true;
        }

        for (String name : policies.keySet()) {
            String desired;

            if (props.containsKey(name)) {
                desired = props.get(name).toString();
            } else {
                desired = DEFAULT.get(name);
            }

            if (Boolean.valueOf(desired) && !Boolean.valueOf(policies.get(name))) {
                return false;
            }
        }

        return true;
    }
}
