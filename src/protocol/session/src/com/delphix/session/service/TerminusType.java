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

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public enum TerminusType {

    UUID(ServiceUUID.class),
    NAME(ServiceName.class);

    private static Map<Class<? extends ServiceTerminus>, TerminusType> typeMap;

    static {
        typeMap = new HashMap<Class<? extends ServiceTerminus>, TerminusType>();

        for (TerminusType type : EnumSet.allOf(TerminusType.class)) {
            typeMap.put(type.getObjectClass(), type);
        }
    }

    private Class<? extends ServiceTerminus> objectClass;

    private TerminusType(Class<? extends ServiceTerminus> objectClass) {
        this.objectClass = objectClass;
    }

    public Class<? extends ServiceTerminus> getObjectClass() {
        return objectClass;
    }

    public static TerminusType getType(Class<? extends ServiceTerminus> objectClass) {
        return typeMap.get(objectClass);
    }
}
