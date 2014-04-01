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

import com.delphix.appliance.logger.Logger;
import com.delphix.session.sasl.SaslMechanism;

import javax.security.sasl.SaslClientFactory;
import javax.security.sasl.SaslServerFactory;

import java.security.Provider;
import java.security.Security;

/**
 * This is the JCA provider that implements some of the SASL mechanisms we need but not existing in the default SUN
 * SASL provider. The following SASL mechanisms are supported currently.
 *
 *   ANONYMOUS client
 *   ANONYMOUS server
 *   PLAIN server
 */
public class SessionSaslProvider extends Provider {

    private static final Logger logger = Logger.getLogger(SessionSaslProvider.class);

    private static final String NAME = "delphix-sasl";
    private static final double VERSION = 1.0;
    private static final String INFO = "Delphix SASL Provider";

    public SessionSaslProvider() {
        super(NAME, VERSION, INFO);

        // Register SASL mechanism factories
        register(SaslMechanism.PLAIN, PlainServerFactory.class);
        register(SaslMechanism.ANONYMOUS, AnonymousClientFactory.class);
        register(SaslMechanism.ANONYMOUS, AnonymousServerFactory.class);
    }

    /**
     * SASL client factories are registered using property names of the form
     *
     *   SaslClientFactory.mechName
     *
     * while SASL server factories are registered using property names of the form
     *
     *   SaslServerFactory.mechName
     *
     * mechName is the mechanism name as returned by SaslClient.getMechanismName() and SaslServer.getMechanismName().
     */
    private void register(String mechanism, Class<?> clazz) {
        String factory;

        if (SaslClientFactory.class.isAssignableFrom(clazz)) {
            factory = SaslClientFactory.class.getSimpleName();
        } else if (SaslServerFactory.class.isAssignableFrom(clazz)) {
            factory = SaslServerFactory.class.getSimpleName();
        } else {
            throw new IllegalArgumentException();
        }

        put(factory + "." + mechanism, clazz.getName());
    }

    /**
     * Install the sasl provider dynamically. It is also possible to configure the sasl provider statically.
     * But that requires updating the system java.security file as well creating and installing a standalone
     * provider jar as a installed or bundled extension.
     */
    public static void install() {
        Provider sasl = new SessionSaslProvider();

        String name = sasl.getName();
        double version = sasl.getVersion();
        String info = sasl.getInfo();

        int preference = Security.addProvider(sasl);

        if (preference >= 0) {
            logger.infof("%s-%f (%s) added at preference position %d", name, version, info, preference);
        }

        sasl = Security.getProvider(name);

        if (sasl == null) {
            logger.errorf("%s-%f (%s) not installed - authentication may fail", name, version, info);
        }
    }
}
