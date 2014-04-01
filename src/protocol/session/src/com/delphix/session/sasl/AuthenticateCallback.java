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

import javax.security.auth.callback.Callback;

/**
 * Authenticate callback is used by the PLAIN SASL mechanism to deliver the password in the SASL response to the
 * application which is responsible for authenticating the user via one of the supported authentication backends.
 */
public class AuthenticateCallback implements Callback {

    private final String password;
    private boolean authenticated;

    public AuthenticateCallback(String password) {
        this.password = password;
    }

    public String getPassword() {
        return password;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }
}
