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

/**
 * Password store supports the retrieval of the password for a given username and optional realm from the security
 * account backend. This is used by password based SASL server mechanisms to verify a client response to a server
 * challenge.
 */
public interface PasswordStore {

    /**
     * Retrieve the password for the username.
     */
    public String getPassword(String username);

    /**
     * Retrieve the password for the username and optional realm.
     */
    public String getPassword(String realm, String username);
}
