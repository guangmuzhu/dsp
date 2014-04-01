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

package com.delphix.session.impl.control;

import com.delphix.session.control.NexusStats;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class GetPeerStatsResponse extends AbstractControlResponse {

    private NexusStats stats;

    public GetPeerStatsResponse() {
        super(GetPeerStatsResponse.class.getSimpleName());
    }

    public NexusStats getStats() {
        return stats;
    }

    public void setStats(NexusStats stats) {
        this.stats = stats;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        stats = (NexusStats) in.readObject();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeObject(stats);
    }

    @Override
    public String toString() {
        return String.format("%s %s", super.toString(), stats);
    }
}
