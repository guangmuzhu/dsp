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

import java.io.Closeable;

/**
 * The main interface to be extended by all Service interfaces exported by the connector. On the Delphix
 * Engine these interfaces are implemented by dynamically generated Proxy objects which forward their
 * method calls over DSP to the concrete Service implementation running in the connector's JVM.
 *
 * === Implementing new Remote Services ===
 *
 * 1) Create a new interface which extends the RemoteService interface.
 *
 * 2) Declare any methods you want to expose remotely in the new Service interface. All arguments to these
 *    methods and the method's return types must be serializable as described in the Serializable class.
 *
 * 3) Create an implementation of the new interface in the connector code base and make it a spring
 *    initialized bean with "scope=prototype".
 */
public interface RemoteService extends Closeable {

    /**
     * Service implementations should not implement this method. It will never be called on concrete
     * Service implementations, only on the dynamically generated remote stubs running on the Delphix
     * Engine. The purpose of this method on remote stubs is to inform the connector that the object
     * the remote stub references is no longer needed by the Engine.
     *
     * AbstractRemoteService provides an optional no-op implementation of this method for services
     * that extend it.
     */
    @Override
    void close();
}
