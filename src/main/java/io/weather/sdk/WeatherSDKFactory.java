package io.weather.sdk;

import io.weather.sdk.config.SDKConfig;
import io.weather.sdk.config.SDKMode;
import io.weather.sdk.exception.WeatherSDKException;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@UtilityClass
public class WeatherSDKFactory {
    private static final Map<String, WeatherSDK> instances = new ConcurrentHashMap<>();
    private static final ReentrantLock factoryLock = new ReentrantLock();

    public WeatherSDK createSDK(String apiKey, SDKMode mode) throws WeatherSDKException {
        return createSDK(apiKey, mode, getDefaultConfigForMode(mode));
    }

    private SDKConfig getDefaultConfigForMode(SDKMode mode) {
        return switch (mode) {
            case ON_DEMAND -> SDKConfig.onDemandConfig();
            case POLLING -> SDKConfig.pollingConfig();
        };
    }

    /**
     * Creates a new WeatherSDK instance with the given API key and mode.

     * If the API key is null or empty, this method throws a WeatherSDKException.
     * If the SDK mode is null, this method throws a WeatherSDKException.
     * If an instance already exists for the given API key, this method throws a WeatherSDKException.
     * Otherwise, this method creates a new WeatherSDK instance, adds it to the instance map, and returns the new instance.
     *
     * @param apiKey the API key to use
     * @param mode the SDK mode to use
     * @return the new WeatherSDK instance
     * @throws WeatherSDKException if the API key is null or empty, or if an instance already exists for the given API key
     */
    public static WeatherSDK createSDK(String apiKey, SDKMode mode, SDKConfig config) throws WeatherSDKException {
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

            WeatherSDK newInstance = WeatherSDK.createInstance(normalizedKey, mode, config);
            instances.put(normalizedKey, newInstance);
            return newInstance;
        } finally {
            factoryLock.unlock();
        }
    }

    /**
     * Removes the WeatherSDK instance associated with the given API key from the
     * factory's instance map. If the instance exists, it is also shut down.

     * This method is thread-safe and does not throw any checked or unchecked
     * exceptions.
     *
     * @param apiKey the API key associated with the instance to remove
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
     * Checks if a WeatherSDK instance exists for the given API key.

     * This method is thread-safe and does not throw any checked or unchecked exceptions.
     *
     * @param apiKey the API key to check for
     * @return true if the instance exists, false otherwise
     */
    public static boolean hasInstance(String apiKey) {
        return apiKey != null && instances.containsKey(apiKey.trim());
    }

    /**
     * Returns the number of WeatherSDK instances currently managed by the factory.

     * This method is thread-safe and does not throw any checked or unchecked exceptions.
     *
     * @return the number of WeatherSDK instances currently managed by the factory
     */
    public static int getInstanceCount() {
        return instances.size();
    }

    /**
     * Shuts down all WeatherSDK instances currently managed by the factory.
     * This method closes all instances and removes them from the factory's instance map.

     * This method is thread-safe and does not throw any checked or unchecked exceptions.
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