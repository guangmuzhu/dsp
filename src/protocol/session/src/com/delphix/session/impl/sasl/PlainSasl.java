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

import javax.security.sasl.SaslException;

/**
 * This class serves as a collection of the RFC defined constants and a few utility methods. It cannot be extended
 * or instantiated.
 *
 * RFC 4616 - message format
 *
 *    message   = [authzid] UTF8NUL authcid UTF8NUL passwd
 *    authcid   = 1*SAFE ; MUST accept up to 255 octets
 *    authzid   = 1*SAFE ; MUST accept up to 255 octets
 *    passwd    = 1*SAFE ; MUST accept up to 255 octets
 *    UTF8NUL   = %x00 ; UTF-8 encoded NUL character
 *
 *    SAFE      = UTF1 / UTF2 / UTF3 / UTF4
 *                ;; any UTF-8 encoded Unicode character except NUL
 *
 *    UTF1      = %x01-7F ;; except NUL
 *    UTF2      = %xC2-DF UTF0
 *    UTF3      = %xE0 %xA0-BF UTF0 / %xE1-EC 2(UTF0) /
 *                %xED %x80-9F UTF0 / %xEE-EF 2(UTF0)
 *    UTF4      = %xF0 %x90-BF 2(UTF0) / %xF1-F3 3(UTF0) /
 *                %xF4 %x80-8F 2(UTF0)
 *    UTF0      = %x80-BF
 */
public final class PlainSasl {

    // Protocol constants
    public static final int NUM_SEPARATORS = 2;
    public static final byte SEPARATOR_BYTE = 0;
    public static final int MAX_FIELD_OCTETS[] = { 255, 255, 255 };
    public static final int MIN_FIELD_OCTETS[] = { 0, 1, 1 };
    public static final int MAX_TOTAL_OCTETS = 255 * 3 + NUM_SEPARATORS;
    public static final int MIN_TOTAL_OCTETS = 1 * 2 + NUM_SEPARATORS;

    /**
     * The private no-arg constructor prevents the class from being instantiated.
     */
    private PlainSasl() {

    }

    public static void validate(byte[] message) throws SaslException {
        if (message == null) {
            throw new SaslException("sasl message expected");
        }

        if (message.length > MAX_TOTAL_OCTETS) {
            throw new SaslException("sasl message length exceeded");
        }

        if (message.length < MIN_TOTAL_OCTETS) {
            throw new SaslException("invalid sasl message");
        }
    }

    public static void validate(String[] userInfo) throws SaslException {
        if (userInfo == null || userInfo.length != 3) {
            throw new SaslException("sasl user info invalid");
        }

        for (int i = 0; i < userInfo.length; i++) {
            int length = userInfo[i].length();

            if (length < MIN_FIELD_OCTETS[i] || length > MAX_FIELD_OCTETS[i]) {
                throw new SaslException("sasl user info invalid");
            }
        }

        if (userInfo[0].isEmpty()) {
            userInfo[0] = userInfo[1];
        }
    }
}
