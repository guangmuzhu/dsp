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

/**
 * This interface describes a service exchange type. It is implemented by an exchange type enum which is used to
 * construct the exchange type registry.
 */
public interface ExchangeType {

    /**
     * Return the object class for the exchange type.
     */
    public Class<? extends ServiceExchange> getObjectClass();
}
