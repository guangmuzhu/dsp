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

package com.delphix.session.impl.control;

import com.delphix.session.service.ServiceExchange;
import com.delphix.session.util.ExchangeType;

public enum ControlExchangeType implements ExchangeType {

    GET_PEER_INFO_REQUEST(GetPeerInfoRequest.class),
    GET_PEER_INFO_RESPONSE(GetPeerInfoResponse.class),
    GET_PEER_STATS_REQUEST(GetPeerStatsRequest.class),
    GET_PEER_STATS_RESPONSE(GetPeerStatsResponse.class),
    RESET_PEER_STATS_REQUEST(ResetPeerStatsRequest.class),
    RESET_PEER_STATS_RESPONSE(ResetPeerStatsResponse.class);

    private Class<? extends ServiceExchange> objectClass;

    private ControlExchangeType(Class<? extends ServiceExchange> objectClass) {
        this.objectClass = objectClass;
    }

    @Override
    public Class<? extends ServiceExchange> getObjectClass() {
        return objectClass;
    }
}
