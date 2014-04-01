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

/**
 * The service architecture defines how network services interact with the underlying session based communication
 * protocol. It is designed with two distinct objectives.
 *
 *   o support of arbitrary applications conforming to the command exchange communication model
 *   o to a lesser degree, support of alternative implementations of the session based communication protocol
 *
 * While there is no real incentive to have alternate implementations of the protocol, a provider based design does
 * offer better modularity and allow the protocol implementation to evolve with minimal impact to applications built
 * on top.
 *
 * The service architecture is comprised of two disjointed sets of interfaces, namely, the service provider interface
 * and the session provider interface. Network service providers offer command execution support accessible to the
 * protocol locally through the session provider interface. The session protocol then extends the service across the
 * network to remote consumers. Network service participants manage sessions and invoke remote services through the
 * session provider interface.
 *
 * The service provider interface consists of the following.
 *
 *   o Service, for service configuration
 *   o ProtocolHandler, for local command execution
 *   o ServiceRequest and ServiceResponse, the semantical service offering contract
 *   o ServiceFuture, for command execution control
 *   o NexusListener, for protocol event delivery
 *
 * The session provider interface consists of the following.
 *
 *   o ServiceTransport, for transport end point management
 *   o ServiceTerminus and ServiceNexus, for nexus end point management
 *   o ClientNexus, for client side remote command execution
 *   o ServerNexus, for server side remote command execution
 *   o Server, for service naming, configuration, and nexus management
 *   o ServerManager, for server lifecycle management
 *   o ClientManager, for client lifecycle management
 *
 * The following diagram illustrates the service architecture for a session based communication protocol.
 *
 *
 *         +-------------------+                         +-------------------+
 *         |    Application    |                         |    Application    | +----------------------+
 *         +--------------+    |                         +--------------+    |                        |
 *         |    Service   |    |                         |    Service   | <--+------------+           |
 *         +--------------+----+                         +--------------+----+            |           |
 *                ^         +                                   ^         +               |           |
 *          Local |         | Remote                      Local |         | Remote        |           |
 *        Execute |         | Execute                   Execute |         | Execute       |           |
 *                +         v                                   +         v               +           v
 *         +-------------------+                         +-------------------+        +-------------------+
 *         |    ClientNexus    |                         |    ServerNexus    | >----> |      Server       |
 *         +-------------------+                         +-------------------+        +-------------------+
 *                +         +                                   +         +                     ^
 *         Client |         | Server                     Server |         | Client              | Service
 *       Terminus ^         v Terminus                 Terminus ^         v Terminus            | Terminus
 *                |         |                                   |         |                     +
 *         +-------------------+                         +-------------------+        +-------------------+
 *         | ServiceTransport  |+                        | ServiceTransport  |+       |    ServerManager  |
 *         +-------------------+|+                       +-------------------+|+      +-------------------+
 *          +-------------------+|                        +-------------------+|                +
 *           +-------------------+                         +-------------------+                |
 *                |         |                                   |         |                     |
 *          x-----+---------+-----------------------------------+---------+---------------------+--------x
 *        xx|     |         |          ForeChannel              |         |                     |       xx
 *        xx|     |         +---->----------->----------->------+         |                     |      xx|
 *         xx     |                                                       |                     |      x |
 *          xx    |                    BackChannel                        |                     |       xxx
 *          |x    +--------------<-----------<-----------<----------------+                     v        xx
 *          xx                                                                              Listening    |x
 *         xx              Session Based Communication Protocol                               Port      xx
 *         xx--------------------------------------------------------------------------------------------x
 *
 *
 * This interface describes an application service offered by the server or client over a session based communication
 * protocol. Each service has a service type that is statically defined. Application intends to offer its service over
 * a session based protocol should implement this interface.
 *
 * The service interface is decoupled from the service executor by design. This allows the application to specify the
 * business logic without having to worry about how it is executed. And conversely, executor service can be specified
 * independent of the business logic.
 */
public interface Service {

    /**
     * Get the service type.
     */
    public ServiceType getType();

    /**
     * Get the service codec responsible for encoding and decoding service payload.
     */
    public ServiceCodec getCodec();
}
