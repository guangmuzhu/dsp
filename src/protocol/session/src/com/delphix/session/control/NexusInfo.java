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

package com.delphix.session.control;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class NexusInfo implements Serializable {

    private String clientTerminus;
    private String serverTerminus;

    private boolean connected;
    private boolean degraded;
    private boolean closed;

    private List<TransportInfo> xports;
    private Map<String, ?> options;

    public NexusInfo() {

    }

    public String getClientTerminus() {
        return clientTerminus;
    }

    public void setClientTerminus(String clientTerminus) {
        this.clientTerminus = clientTerminus;
    }

    public String getServerTerminus() {
        return serverTerminus;
    }

    public void setServerTerminus(String serverTerminus) {
        this.serverTerminus = serverTerminus;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public boolean isDegraded() {
        return degraded;
    }

    public void setDegraded(boolean degraded) {
        this.degraded = degraded;
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public Map<String, ?> getOptions() {
        return options;
    }

    public void setOptions(Map<String, ?> options) {
        this.options = options;
    }

    public List<TransportInfo> getTransports() {
        return xports;
    }

    public void setTransports(List<TransportInfo> xports) {
        this.xports = xports;
    }
}
