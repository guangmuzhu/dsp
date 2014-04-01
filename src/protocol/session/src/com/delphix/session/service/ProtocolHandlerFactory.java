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

package com.delphix.session.service;

import java.util.Collection;

/**
 * When a DSP server starts a new session it will call a ProtocolHandlerFactory to get the
 * ProtocolHandler that should be used for the new session. Implementations of this interface
 * may return the same ProtocolHandler for all sessions or a new handler for each session,
 * depending on the application's needs.
 */
public interface ProtocolHandlerFactory {
    Collection<? extends ProtocolHandler<?>> getHandlers(ServiceTerminus clientTerminus);
}
