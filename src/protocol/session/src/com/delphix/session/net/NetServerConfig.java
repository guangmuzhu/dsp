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

package com.delphix.session.net;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.*;

/**
 * This class describes the network server configuration. It allows a network service to be accessed from a subset of
 * the network addresses or interfaces configured on the host.
 */
public class NetServerConfig {

    private final Set<InetAddress> addresses = new HashSet<InetAddress>();

    public boolean contains(InetAddress address) {
        return addresses.contains(address);
    }

    public void addAll() throws SocketException {
        Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();

        for (NetworkInterface net : Collections.list(nets)) {
            add(net.getName());
        }
    }

    public boolean add(InetAddress address) throws SocketException {
        NetworkInterface net = NetworkInterface.getByInetAddress(address);

        if (net == null) {
            throw new IllegalArgumentException("no network interface with the specified address");
        }

        return addresses.add(address);
    }

    public boolean add(String name) throws SocketException {
        NetworkInterface net = NetworkInterface.getByName(name);

        if (net == null) {
            throw new IllegalArgumentException("no network interface with the specified name");
        }

        Enumeration<InetAddress> addrs = net.getInetAddresses();

        return addresses.addAll(Collections.list(addrs));
    }

    public boolean remove(InetAddress address) {
        return addresses.remove(address);
    }

    public boolean remove(String name) throws SocketException {
        NetworkInterface net = NetworkInterface.getByName(name);

        if (net == null) {
            throw new IllegalArgumentException("no network interface with the specified name");
        }

        Enumeration<InetAddress> addrs = net.getInetAddresses();

        return addresses.removeAll(Collections.list(addrs));
    }

    public Collection<InetAddress> getAll() {
        return addresses;
    }

    public boolean isEmpty() {
        return addresses.isEmpty();
    }

    public void clear() {
        addresses.clear();
    }
}
