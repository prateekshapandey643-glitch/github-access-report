package com.example.githubaccessreport.util;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal thread-safe in-memory TTL cache. Sufficient for a single-instance
 * deployment; for multiple replicas, swap this out for a shared store
 * (e.g. Redis) behind the same {@code get/put/invalidate} shape.
 */
public class TtlCache<K, V> {

    private record Entry<V>(V value, long expiresAtMillis) {
    }

    private final ConcurrentHashMap<K, Entry<V>> store = new ConcurrentHashMap<>();
    private final long ttlMillis;

    public TtlCache(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    public V get(K key) {
        Entry<V> entry = store.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() > entry.expiresAtMillis()) {
            store.remove(key, entry);
            return null;
        }
        return entry.value();
    }

    public void put(K key, V value) {
        store.put(key, new Entry<>(value, System.currentTimeMillis() + ttlMillis));
    }

    public void invalidate(K key) {
        store.remove(key);
    }
}
