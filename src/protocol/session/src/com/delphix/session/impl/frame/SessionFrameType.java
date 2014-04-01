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

package com.delphix.session.impl.frame;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Session frame type enumeration.
 */
public enum SessionFrameType {

    CONNECT_REQUEST(ConnectRequest.class),
    CONNECT_RESPONSE(ConnectResponse.class),
    AUTHENTICATE_REQUEST(AuthenticateRequest.class),
    AUTHENTICATE_RESPONSE(AuthenticateResponse.class),
    NEGOTIATE_REQUEST(NegotiateRequest.class),
    NEGOTIATE_RESPONSE(NegotiateResponse.class),
    COMMAND_REQUEST(CommandRequest.class),
    COMMAND_RESPONSE(CommandResponse.class),
    TASKMGMT_REQUEST(TaskMgmtRequest.class),
    TASKMGMT_RESPONSE(TaskMgmtResponse.class),
    PING_REQUEST(PingRequest.class),
    PING_RESPONSE(PingResponse.class),
    LOGOUT_REQUEST(LogoutRequest.class),
    LOGOUT_RESPONSE(LogoutResponse.class);

    private static Map<Class<? extends SessionFrame>, SessionFrameType> typeMap;

    static {
        typeMap = new HashMap<Class<? extends SessionFrame>, SessionFrameType>();

        for (SessionFrameType type : EnumSet.allOf(SessionFrameType.class)) {
            typeMap.put(type.getObjectClass(), type);
        }
    }

    private Class<? extends SessionFrame> objectClass;

    private SessionFrameType(Class<? extends SessionFrame> objectClass) {
        this.objectClass = objectClass;
    }

    public Class<? extends SessionFrame> getObjectClass() {
        return objectClass;
    }

    public static SessionFrameType getType(Class<? extends SessionFrame> objectClass) {
        return typeMap.get(objectClass);
    }
}
