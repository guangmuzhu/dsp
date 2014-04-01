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
 * Copyright (c) 2014 by Delphix. All rights reserved.
 */

package com.delphix.session.module.rmi;

import com.delphix.session.module.rmi.protocol.*;
import com.delphix.session.service.ServiceExchange;
import com.delphix.session.util.ExchangeType;

public enum RmiTestExchangeType implements ExchangeType {
    // RMI
    OBJECT_CREATE_REQUEST(ObjectCreateRequest.class),
    OBJECT_CREATE_RESPONSE(ObjectCreateResponse.class),
    OBJECT_DESTROY_REQUEST(ObjectDestroyRequest.class),
    OBJECT_DESTROY_RESPONSE(ObjectDestroyResponse.class),
    METHOD_CALL_REQUEST(MethodCallRequest.class),
    METHOD_CALL_RESPONSE(MethodCallResponse.class);

    private Class<? extends ServiceExchange> objectClass;

    private RmiTestExchangeType(Class<? extends ServiceExchange> objectClass) {
        this.objectClass = objectClass;
    }

    @Override
    public Class<? extends ServiceExchange> getObjectClass() {
        return objectClass;
    }
}
