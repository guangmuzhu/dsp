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

/**
 * This interface describes a session transport scheduler.
 */
public interface SessionTransportScheduler {

    /**
     * Attach the transport to the scheduler.
     */
    public void attach(SessionTransport xport);

    /**
     * Detach the transport from the scheduler.
     */
    public void detach(SessionTransport xport);

    /**
     * Remove all transports from the scheduler.
     */
    public void clear();

    /**
     * Check if the scheduler has the transport.
     */
    public boolean contains(SessionTransport xport);

    /**
     * Check if the scheduler has any transport.
     */
    public boolean isEmpty();

    /**
     * Schedule the exchange on a transport.
     */
    public SessionTransport schedule(SessionExchange exchange);

    /**
     * Schedule a transport.
     */
    public SessionTransport schedule();
}
