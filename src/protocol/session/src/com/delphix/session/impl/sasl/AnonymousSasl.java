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

import org.apache.commons.validator.routines.EmailValidator;

import javax.security.sasl.SaslException;

/**
 * This class serves as a collection of the RFC defined constants and a few utility methods. It cannot be extended
 * or instantiated.
 *
 * RFC 4505 - message format
 *
 *     message     = [ email / token ]
 *                   ;; to be prepared in accordance with Section 3
 *
 *     UTF1        = %x00-3F / %x41-7F ;; less '@' (U+0040)
 *     UTF2        = %xC2-DF UTF0
 *     UTF3        = %xE0 %xA0-BF UTF0 / %xE1-EC 2(UTF0) /
 *                   %xED %x80-9F UTF0 / %xEE-EF 2(UTF0)
 *     UTF4        = %xF0 %x90-BF 2(UTF0) / %xF1-F3 3(UTF0) /
 *                   %xF4 %x80-8F 2(UTF0)
 *     UTF0        = %x80-BF
 *
 *     TCHAR       = UTF1 / UTF2 / UTF3 / UTF4
 *                   ;; any UTF-8 encoded Unicode character
 *                   ;; except '@' (U+0040)
 *
 *     email       = addr-spec
 *                   ;; as defined in [IMAIL]
 *
 *     token       = 1*255TCHAR
 */
public final class AnonymousSasl {

    // Protocol constants
    public static final int MIN_TOKEN_LENGTH = 1;
    public static final int MAX_TOKEN_LENGTH = 255;
    public static final int MIN_TOTAL_OCTETS = 1;
    public static final int MAX_TOTAL_OCTETS = 1020;
    public static final String ILLEGAL_TOKEN_CHARS = "@";

    /**
     * The private no-arg constructor prevents the class from being instantiated.
     */
    private AnonymousSasl() {

    }

    public static void validate(String name) throws SaslException {
        if (name == null) {
            throw new SaslException("email or token required");
        }

        EmailValidator validator = EmailValidator.getInstance();

        if (!validator.isValid(name)) {
            int length = name.length();

            if (length < MIN_TOKEN_LENGTH || length > MAX_TOKEN_LENGTH) {
                throw new SaslException("token length limit exceeded");
            } else if (name.contains(ILLEGAL_TOKEN_CHARS)) {
                throw new SaslException("token contains illegal characters");
            }
        }
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
}
