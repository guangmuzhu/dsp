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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * This class describes a registry of distinct objects each identified by its own unique key.
 */
public class ObjectRegistry<K, V> {

    private final Map<K, V> registry = new HashMap<K, V>();

    public void register(K key, V value) {
        if (registry.containsKey(key)) {
            throw new IllegalStateException("object already exists " + key);
        }

        registry.put(key, value);
    }

    public V unregister(K key) {
        V value = registry.remove(key);

        if (value == null) {
            throw new IllegalStateException("object not found " + key);
        }

        return value;
    }

    public V locate(K key) {
        return registry.get(key);
    }

    public void keys(Set<? super K> keys) {
        keys.addAll(registry.keySet());
    }

    public void values(Set<? super V> values) {
        values.addAll(registry.values());
    }

    public boolean isEmpty() {
        return registry.isEmpty();
    }

    public void clear() {
        registry.clear();
    }

    public boolean contains(K key) {
        return registry.containsKey(key);
    }

    public int size() {
        return registry.size();
    }

    @Override
    public String toString() {
        return registry.toString();
    }

    public static <K, V> ObjectRegistry<K, V> create() {
        return new ObjectRegistry<K, V>();
    }
}
