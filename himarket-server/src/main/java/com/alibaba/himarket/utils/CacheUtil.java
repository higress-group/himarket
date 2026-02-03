/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.alibaba.himarket.utils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class CacheUtil {

    /**
     * Caffeine cache
     *
     * @param expireAfterWrite
     * @param <K>
     * @param <V>
     * @return
     */
    public static <K, V> Cache<K, V> newCache(long expireAfterWrite) {
        return Caffeine.newBuilder()
                .initialCapacity(10)
                .maximumSize(10000)
                .expireAfterWrite(expireAfterWrite, TimeUnit.MINUTES)
                .build();
    }

    /**
     * Caffeine cache with expire callback
     *
     * @param expireAfterWrite
     * @param onExpire
     * @param <K>
     * @param <V>
     * @return
     */
    public static <K, V> Cache<K, V> newCache(long expireAfterWrite, Consumer<V> onExpire) {
        return Caffeine.newBuilder()
                .initialCapacity(10)
                .maximumSize(10000)
                .expireAfterWrite(expireAfterWrite, TimeUnit.MINUTES)
                .removalListener(
                        (K key, V value, RemovalCause cause) -> {
                            if (cause.equals(RemovalCause.EXPIRED)
                                    && value != null
                                    && onExpire != null) {
                                onExpire.accept(value);
                            }
                        })
                .build();
    }

    /**
     * Create LRU cache with time-based eviction
     *
     * @param expireAfterAccess expire after N seconds of no access
     * @param <K>               key type
     * @param <V>               value type
     * @return Cache instance
     */
    public static <K, V> Cache<K, V> newLRUCache(long expireAfterAccess) {
        return Caffeine.newBuilder()
                .initialCapacity(10)
                .maximumSize(10000)
                .expireAfterAccess(expireAfterAccess, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Create LRU cache with time-based eviction and removal listener
     *
     * @param expireAfterAccess expire after N seconds of no access
     * @param removalListener   listener to be invoked when an entry is removed
     * @param <K>               key type
     * @param <V>               value type
     * @return Cache instance
     */
    public static <K, V> Cache<K, V> newLRUCache(
            long expireAfterAccess, RemovalListener<K, V> removalListener) {
        return Caffeine.newBuilder()
                .initialCapacity(10)
                .maximumSize(10000)
                .expireAfterAccess(expireAfterAccess, TimeUnit.SECONDS)
                .removalListener(removalListener)
                .build();
    }
}
