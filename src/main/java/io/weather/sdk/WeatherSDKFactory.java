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

    public static boolean hasInstance(String apiKey) {
        return apiKey != null && instances.containsKey(apiKey.trim());
    }

    public static int getInstanceCount() {
        return instances.size();
    }

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