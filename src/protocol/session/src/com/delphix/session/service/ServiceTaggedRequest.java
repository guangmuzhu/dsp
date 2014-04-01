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
 * This class represents a request with a tag. The tag may be used to associate a request with an extended task that
 * consists of multiple protocol exchanges. For example, the request may be identified to be part of a data stream.
 * The content of the tag is opaque but it should be unique among all outstanding tasks for identification purpose.
 * All requests sharing the same tag are processed in the exact same order as they are submitted.
 */
public interface ServiceTaggedRequest extends ServiceRequest {

    /**
     * Return the request tag.
     */
    public Object getTag();
}
