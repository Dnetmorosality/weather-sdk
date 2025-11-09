package io.weather.sdk.cache;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author rus.sadykov
 * 07.11.2025
 */
public class LRUCache<K, V> {
    private final int maxSize;
    private final ConcurrentHashMap<K, V> cache;
    private final ConcurrentLinkedDeque<K> accessOrder;
    private final ReentrantLock lock;

    public LRUCache(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("Cache size must be positive");
        }
        this.maxSize = maxSize;
        this.cache = new ConcurrentHashMap<>(maxSize);
        this.accessOrder = new ConcurrentLinkedDeque<>();
        this.lock = new ReentrantLock();
    }

    /**
     * Retrieves the value associated with the given key from the cache.
     * If the key is null, returns an empty Optional.
     * If the key is not present in the cache, returns an empty Optional.
     * If the key is present in the cache, returns an Optional containing the associated value.
     * The retrieved value is moved to the top of the access order.
     *
     * @param key the key to retrieve the value for
     * @return an Optional containing the associated value, or an empty Optional if the key is not present
     */
    public Optional<V> get(K key) {
        if (key == null) return Optional.empty();

        lock.lock();
        try {
            V value = cache.get(key);
            if (value != null) {
                accessOrder.remove(key);
                accessOrder.addFirst(key);
                return Optional.of(value);
            }
            return Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Associates the given key with the given value in the cache.
     * If the key is null, or the value is null, this method does nothing.
     * If the key already exists in the cache, this method removes the existing key-value pair from the access order.
     * If the cache is full (i.e. the cache size is equal to the maximum allowed size), this method removes the least recently used key-value pair from the cache.
     * The newly associated key-value pair is added to the top of the access order.
     *
     * @param key the key to associate with the given value
     * @param value the value to associate with the given key
     */
    public void put(K key, V value) {
        if (key == null || value == null) return;

        lock.lock();
        try {
            if (cache.containsKey(key)) {
                accessOrder.remove(key);
            }
            else if (cache.size() >= maxSize) {
                K oldestKey = accessOrder.pollLast();
                if (oldestKey != null) {
                    cache.remove(oldestKey);
                }
            }
            cache.put(key, value);
            accessOrder.addFirst(key);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes the key-value pair associated with the given key from the cache.
     * If the key does not exist in the cache, this method does nothing.
     * This method removes the given key from both the cache and the access order.
     *
     * @param key the key to remove from the cache
     */
    public void remove(K key) {
        lock.lock();
        try {
            cache.remove(key);
            accessOrder.remove(key);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the number of entries in the cache.
     *
     * @return the number of entries in the cache
     */
    public int size() {
        return cache.size();
    }

    /**
     * Removes all key-value pairs from the cache.
     * This method is thread-safe and does not throw any checked or unchecked exceptions.
     */
    public void clear() {
        lock.lock();
        try {
            cache.clear();
            accessOrder.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks if the cache contains a key-value pair associated with the given key.
     *
     * @param key the key to check for
     * @return true if the cache contains a key-value pair associated with the given key, false otherwise
     */
    public boolean containsKey(K key) {
        return cache.containsKey(key);
    }
}
