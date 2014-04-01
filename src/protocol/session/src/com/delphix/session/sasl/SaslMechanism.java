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

import java.util.Map;

/**
 * SASL is conceptually a framework that provides an abstraction layer between protocols and mechanisms as illustrated
 * in the following diagram (copied from the RFC).
 *
 *                 SMTP    LDAP    XMPP   Other protocols ...
 *                    \       |    |      /
 *                     \      |    |     /
 *                    SASL abstraction layer
 *                     /      |    |     \
 *                    /       |    |      \
 *             EXTERNAL   GSSAPI  PLAIN   Other mechanisms ...
 *
 * Included here is an incomplete listing of SASL mechanisms registered with IANA. See link below for a complete
 * listing.
 *
 *  http://www.iana.org/assignments/sasl-mechanisms/sasl-mechanisms.xml
 *
 * Under SASL, each authentication attempt consists of a series of challenges and responses exchanged between the
 * client and the server, regardless of the mechanism in use. The client and the server each maintains a SASL
 * authentication context for the duration of the authentication process.
 */
public interface SaslMechanism {

    public static String ANONYMOUS = "ANONYMOUS";
    public static String CRAM_MD5 = "CRAM-MD5";
    public static String DIGEST_MD5 = "DIGEST-MD5";
    public static String EXTERNAL = "EXTERNAL";
    public static String GSSAPI = "GSSAPI";
    public static String PLAIN = "PLAIN";

    /**
     * Get the name of the SASL mechanism.
     */
    public String getMechanism();

    /**
     * Get the property map used to configure the SASL mechanism.
     */
    public Map<String, ?> getProperties();
}
