package io.weather.sdk;

import io.weather.sdk.config.SDKMode;
import io.weather.sdk.exception.WeatherSDKException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author rus.sadykov
 * 05.11.2025
 */
public class WeatherSDKFactory {
    private static final Map<String, WeatherSDK> instances = new ConcurrentHashMap<>();
    private static final ReentrantLock factoryLock = new ReentrantLock();

    private WeatherSDKFactory() {}

    /**
     * Creates a new WeatherSDK instance with the given API key and mode.
     *
     * If an SDK instance already exists for the given API key, it will throw a
     * WeatherSDKException.
     *
     * @param apiKey the API key to use for the SDK instance
     * @param mode the mode of the SDK instance
     * @return the created SDK instance
     * @throws WeatherSDKException if the API key is invalid, or an SDK instance already exists for the given API key
     */
    public static WeatherSDK createSDK(String apiKey, SDKMode mode) throws WeatherSDKException {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new WeatherSDKException("API key cannot be null or empty");
        }
        if (mode == null) {
            throw new WeatherSDKException("SDK mode cannot be null");
        }

        String normalizedKey = apiKey.trim();

        factoryLock.lock();
        try {
            WeatherSDK existingInstance = instances.get(normalizedKey);
            if (existingInstance != null) {
                throw new WeatherSDKException("SDK instance already exists for this API key");
            }

            WeatherSDK newInstance = WeatherSDK.createInstance(normalizedKey, mode);
            instances.put(normalizedKey, newInstance);
            return newInstance;
        } finally {
            factoryLock.unlock();
        }
    }

    /**
     * Removes the SDK instance associated with the given API key.
     * If no instance exists for the given API key, this method does nothing.
     *
     * @param apiKey the API key associated with the SDK instance to be removed
     */
    public static void removeSDK(String apiKey) {
        if (apiKey != null) {
            factoryLock.lock();
            try {
                WeatherSDK instance = instances.remove(apiKey.trim());
                if (instance != null) {
                    instance.close();
                }
            } finally {
                factoryLock.unlock();
            }
        }
    }

    /**
     * Returns true if an SDK instance associated with the given API key exists,
     * false otherwise.
     *
     * @param apiKey the API key associated with the SDK instance to check
     * @return true if an SDK instance associated with the given API key exists, false otherwise
     */
    public static boolean hasInstance(String apiKey) {
        return apiKey != null && instances.containsKey(apiKey.trim());
    }

    /**
     * Returns the number of WeatherSDK instances created by this factory.
     *
     * @return the number of WeatherSDK instances created by this factory
     */
    public static int getInstanceCount() {
        return instances.size();
    }

    /**
     * Shuts down all WeatherSDK instances created by this factory.
     * This method is thread-safe and can be safely called from multiple threads.
     *
     * After calling this method, all WeatherSDK instances will be closed and
     * the factory will be reset to its initial state.
     *
     * This method does not throw any checked or unchecked exceptions.
     */
    public static void shutdownAll() {
        factoryLock.lock();
        try {
            instances.values().forEach(WeatherSDK::close);
            instances.clear();
        } finally {
            factoryLock.unlock();
        }
    }
}