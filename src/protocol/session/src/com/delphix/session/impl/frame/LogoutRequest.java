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
 * This class describes the logout request.
 *
 * Client may want to de-commission one of the connections from a session, or it may want to tear down the entire
 * session, all in an orderly manner. Connection logout allows the client to close the connection over which the
 * logout request is sent or to close the session of which the connection is a member. We only support the latter.
 * Without it, session state, including outstanding service requests, service response cache, slot tables, and
 * various command queues, and application state associated with the session, may lie around until session state
 * timeout is reached.
 *
 * Client should avoid logging out of the connection or session when there are outstanding commands associated with
 * each in the fore channel; it should either complete or cancel those commands before proceeding to logout. Client
 * should also avoid issuing new commands after logout has been requested on the session. Failing the above, it is
 * possible for the commands to be failed with nexus reset exception.
 *
 * The activities over the back channel are driven by the server and therefore completely asynchronous with respect
 * to the client. As such, connection logout may be initiated by the client while commands over the back channel
 * are still in flight. While we could treat the back channel in brute-force manner when it comes to the handling
 * of outstanding commands during logout, it is possible to handle it more gracefully. To do that, first of all, the
 * session continues to be operational while logout is in progress. The backchannel continues to process outstanding
 * as well as new commands even though the client has announced its intention to stop. Secondly, we support a logout
 * timeout whose value is negotiated during login. Server may take advantage of this window to quiesce any commands
 * still in flight on the backchannel before proceeding to logout. It should, however, avoid issuing new commands
 * after being notified of the logout request. The timeout ensures that the client is not kept waiting indefinitely
 * if the server could not respond in time. Commands in flight over the forechannel may be dropped as soon as the
 * server is ready to logout. But it is the client responsibility to ensure that doesn't happen.
 *
 * Logout may be issued from the client when the session is being closed. It is only attempted if the session has
 * been successfully established in the past and still remains in healthy state. There is no point in cleaning up
 * the session state before it is even established. If logout fails due to transport reset, we would retry it over
 * another healthy transport until none is left. Currently, we give up on logout after the session has lost all of
 * its transports even though that could be temporary.
 *
 *   o logoutSession
 *
 * This flag indicates whether to logout the connection only or to logout the entire session. The former allows the
 * connection to be de-commissioned from the session gracefully while the latter allows the session to be closed.
 */
public class LogoutRequest extends OperateRequest {

    private boolean logoutSession;

    public LogoutRequest() {
        super();
    }

    @Override
    public boolean isForeChannel() {
        return true;
    }

    public boolean isLogoutSession() {
        return logoutSession;
    }

    public void setLogoutSession(boolean logoutSession) {
        this.logoutSession = logoutSession;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        logoutSession = in.readBoolean();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeBoolean(logoutSession);
    }
}
