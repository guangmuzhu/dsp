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

package com.delphix.session.impl.common;

import com.delphix.session.service.NexusListener;
import com.delphix.session.util.Event;
import com.delphix.session.util.EventSource;

/**
 * This interface describes a session event source. It generates new session lifecycle events and posts them to the
 * dispatcher for processing. The class that implements this interface is responsible for the content of the event
 * as well as the semantics of event notification.
 */
public interface SessionEventSource extends EventSource {

    /**
     * Notify the listener after it has been added.
     */
    public void notify(NexusListener listener);

    /**
     * Notify the listener of the new session event.
     */
    public void notify(NexusListener listener, Event event);
}
