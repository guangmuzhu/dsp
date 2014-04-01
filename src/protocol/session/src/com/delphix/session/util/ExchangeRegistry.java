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

package com.delphix.session.util;

import com.delphix.session.service.ServiceExchange;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * This class describes a service exchange registry that maintains the mappings between a service exchange java
 * class and an external type code for all exchanges defined for a given protocol service. The external type code
 * is used to efficiently represent the type of the object over the wire. Each service exchange must have a unique
 * type code within the service it is defined.
 *
 * To create an exchange type registry, one must first define a exchange type enum that implements the ExchangeType
 * interface. The enum must include all exchanges defined or used in the protocol service. The class of the type
 * enum is then used to construct the type registry.
 */
public class ExchangeRegistry<E extends Enum<E>> {

    private Map<Class<? extends ServiceExchange>, E> typeMap;
    private ExchangeType[] values;

    private ExchangeRegistry(Class<E> clazz) {
        EnumSet<E> set = EnumSet.allOf(clazz);

        typeMap = new HashMap<Class<? extends ServiceExchange>, E>();
        values = new ExchangeType[set.size()];

        for (E typeEnum : set) {
            ExchangeType type = (ExchangeType) typeEnum;

            typeMap.put(type.getObjectClass(), typeEnum);
            values[typeEnum.ordinal()] = type;
        }
    }

    public int getObjectType(Class<? extends ServiceExchange> clazz) {
        return typeMap.get(clazz).ordinal();
    }

    public Class<? extends ServiceExchange> getObjectClass(int type) {
        return values[type].getObjectClass();
    }

    public static <E extends Enum<E>> ExchangeRegistry<E> create(Class<E> clazz) {
        return new ExchangeRegistry<E>(clazz);
    }
}
