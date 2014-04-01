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

/**
 * This class describes a session event. It is created by the event source and processed by the event manager.
 */
public abstract class Event implements Runnable {

    protected EventSource source;

    public Event() {
        this(null);
    }

    public Event(EventSource source) {
        this.source = source;
    }

    /**
     * Get the source of the event.
     */
    public EventSource getSource() {
        return source;
    }
}
