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
 * Password authenticator supports user authentication based on user name and password. This is used by the PLAIN SASL
 * mechanism to authenticate a user. In contrast to the password store, which retrieves the password and hands it over
 * to the SASL mechanism implementation, the authenticator gets the user name and password from the SASL mechanism and
 * passes them to one of the authentication backends available on the server.
 */
public interface PasswordAuthenticator {

    /**
     * Authenticate the user and return true if successful.
     */
    public boolean authenticate(String username, String password);
}
