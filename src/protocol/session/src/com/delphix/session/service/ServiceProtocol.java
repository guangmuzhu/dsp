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

package com.delphix.session.service;

/**
 * Service name and transport protocol port number assignment for Delphix Session Protocol
 *
 *   dlpx-sp            8415        tcp    Delphix Session Protocol
 *
 * See http://www.iana.org/assignments/service-names-port-numbers for reference.
 */
public final class ServiceProtocol {

    // Service or protocol name
    public static final String PROTOCOL = "dlpx-sp";

    // TCP port number
    public static final int PORT = 8415;
}
