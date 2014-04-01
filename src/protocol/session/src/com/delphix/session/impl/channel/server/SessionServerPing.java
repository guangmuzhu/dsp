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

package com.delphix.session.impl.channel.server;

import com.delphix.session.impl.frame.PingResponse;
import com.delphix.session.impl.frame.RequestFrame;

public class SessionServerPing extends SessionServerExchange {

    public SessionServerPing(SessionServerChannel channel, RequestFrame request) {
        super(channel, request);
    }

    @Override
    protected void createExchange() {
        PingResponse response = new PingResponse();

        response.setForeChannel(channel.isFore());

        response.setExchangeID(exchangeID);

        setResponse(response);
    }

    @Override
    public void receive() {
        channel.refresh(request);
        channel.getSibling().refresh(request);

        // Send the response back
        send();
    }
}
