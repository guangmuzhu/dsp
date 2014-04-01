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

package com.delphix.session.test;

import com.delphix.session.sasl.PasswordStore;
import com.delphix.session.sasl.UserMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * User database implementation.
 */
public class UserDatabase implements PasswordStore, UserMapper {

    // Sample password database
    private static final Map<String, String> database = new HashMap<String, String>();
    private static String[] realms = new String[] { "foo.realm", "bar.realm", "server.realm" };

    {
        database.put("foo.realm/foo", "foo");
        database.put("bar.realm/bar", "bar");
        database.put("server.realm/username", "password");
        database.put("username", "password");
    }

    public UserDatabase() {

    }

    public String[] getRealms() {
        return realms;
    }

    @Override
    public String getPassword(String username) {
        return getPassword(null, username);
    }

    @Override
    public String authorize(String authcid, String authzid) {
        return authorize(null, authcid, authzid);
    }

    @Override
    public String getPassword(String realm, String username) {
        String key;

        if (realm != null) {
            key = realm + "/" + username;
        } else {
            key = username;
        }

        return database.get(key);
    }

    @Override
    public String authorize(String realm, String authcid, String authzid) {
        // Always authorize and use the same authorization user as the one specified
        return authzid;
    }
}
