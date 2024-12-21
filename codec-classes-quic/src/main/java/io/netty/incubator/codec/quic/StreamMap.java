/*
 * Copyright 2022 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.incubator.codec.quic;

/**
 * A generic stream-based map implementation using ArrayRingBuffer.
 * This class provides basic map operations with integer keys and generic values.
 *
 * @param <V> the type of values stored in the map
 */
public class StreamMap<V> {
    /** The underlying ring buffer to store values */
    private final ArrayRingBuffer<V> values;

    /**
     * Constructs an empty StreamMap.
     * Initializes the internal ArrayRingBuffer.
     */
    public StreamMap() {
        this.values = new ArrayRingBuffer<>();
    }

    /**
     * Stores a value in the map with the specified key.
     *
     * @param key   the integer key at which to store the value
     * @param value the value to be stored
     * @throws IllegalArgumentException if the key is negative
     * @throws NullPointerException if the value is null
     */
    public void put(int key, V value) {
        values.put(key, value);
    }

    /**
     * Retrieves the value associated with the specified key.
     *
     * @param index the key whose associated value is to be returned
     * @return the value associated with the specified key, or null if no value is present
     * @throws IllegalArgumentException if the index is negative
     */
    public V get(int index) {
        V value = values.get(index);
        if (value != null) {
            return value;
        } else {
            return null;
        }
    }

    /**
     * Returns the number of key-value mappings in this map.
     *
     * @return the number of key-value mappings in this map
     */
    public int size() {
        return values.size();
    }

    /**
     * Removes the mapping for the specified key if present.
     *
     * @param index the key whose mapping is to be removed from the map
     * @throws IllegalArgumentException if the index is negative
     */
    public void remove(int index) {
        values.remove(index);
    }
}