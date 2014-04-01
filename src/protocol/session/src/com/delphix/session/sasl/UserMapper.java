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
 * User mapper checks if a mapping exists from the given authenticated user to the authorization user. This is used
 * by SASL server mechanisms for authorization purposes.
 */
public interface UserMapper {

    /**
     * Determine whether the authenticated user is allowed to act on behalf of the requested authorization id, and
     * also to obtain the canonicalized name of the authorized user (if canonicalization is applicable). If the user
     * is authorized, the authorization id is returned; otherwise, null is returned.
     */
    public String authorize(String authcid, String authzid);

    /**
     * Determine whether the authenticated user is allowed to act on behalf of the requested authorization id, and
     * also to obtain the canonicalized name of the authorized user (if canonicalization is applicable). If the user
     * is authorized, the authorization id is returned; otherwise, null is returned.
     */
    public String authorize(String realm, String authcid, String authzid);
}
