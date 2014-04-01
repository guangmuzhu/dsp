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
import java.util.*;

public class NexusStats implements Serializable {

    private Map<String, ?> stats;

    public NexusStats() {

    }

    public Map<String, ?> getStats() {
        return stats;
    }

    public void setStats(Map<String, ?> stats) {
        this.stats = stats;
    }

    public Object getStat(String stat) {
        return stats.get(stat);
    }

    public String format() {
        List<String> keys = new ArrayList<String>();

        keys.addAll(stats.keySet());
        Collections.sort(keys);

        StringBuilder builder = new StringBuilder();
        Formatter format = new Formatter(builder);

        try {
            for (String stat : keys) {
                format.format("%32s: %s\n", stat, stats.get(stat));
            }
        } finally {
            format.close();
        }

        return builder.toString();
    }
}
