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

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * This class describes a ping request. Ping is a no-op exchange generated internally by the session protocol. There
 * are a couple of uses for the session level ping.
 *
 * First, we use it as a handshake mechanism to help avoid race between the client and the server. Due to the duplex
 * nature of the session, the server could issue a command over the back channel immediately following session login
 * even before the client has had a chance to fully complete its end of the runtime configuration. This could lead to
 * codec failures due to the mismatch configuration. To prevent this from happening, we have the client issue a ping
 * to the server as its cue to commence the back channel processing.
 *
 * The other intended use is for periodic session level keep alive. It supersedes transport keepalive since it covers
 * application liveness in addition to transport liveness. The intention is to prevent dead applications with live
 * transports from taking up server side resources. It's more useful in an open environment with potentially large
 * number of 3rd party clients; hence a relatively low priority for us.
 */
public class PingRequest extends OperateRequest {

    public PingRequest() {
        super();
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
    }
}
