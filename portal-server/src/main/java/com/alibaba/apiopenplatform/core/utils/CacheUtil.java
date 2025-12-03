package com.alibaba.apiopenplatform.core.utils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.TimeUnit;

/**
 * @author zh
 */
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
}
