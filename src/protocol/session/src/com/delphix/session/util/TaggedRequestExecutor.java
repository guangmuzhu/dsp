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

import com.delphix.session.service.ServiceRequest;
import com.delphix.session.service.ServiceTaggedRequest;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * This class provides ordered execution of tagged service request. The tag must be unique among all outstanding
 * tasks for ordering to take effect. The special null tag is also supported.
 */
public class TaggedRequestExecutor extends AbstractOrderedExecutor {

    /**
     * Create a tagged request executor using the direct handoff model.
     */
    public TaggedRequestExecutor() {
        super();
    }

    /**
     * Create a tagged request executor using the unbounded queue model.
     */
    public TaggedRequestExecutor(int corePoolSize) {
        super(corePoolSize);
    }

    /**
     * Create a tagged request executor with support of the generic bounded queue model.
     */
    public TaggedRequestExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    @Override
    protected EventSource getSource(ServiceRequest request) {
        EventSource source = null;

        if (request instanceof ServiceTaggedRequest) {
            ServiceTaggedRequest tagged = (ServiceTaggedRequest) request;
            Object tag = tagged.getTag();

            source = new TaggedRequestSource(tag);
        }

        return source;
    }

    private class TaggedRequestSource implements EventSource {

        private final Object tag;

        public TaggedRequestSource(Object tag) {
            this.tag = tag;
        }

        @Override
        public boolean isTerminal() {
            return false;
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof TaggedRequestSource)) {
                return false;
            }

            TaggedRequestSource source = (TaggedRequestSource) object;

            if (tag == source.tag) {
                return true;
            } else if (tag == null) {
                return false;
            } else {
                return tag.equals(source.tag);
            }
        }

        @Override
        public int hashCode() {
            return tag != null ? tag.hashCode() : 0;
        }
    }
}
