package io.weather.sdk;

import io.weather.sdk.config.SDKMode;
import io.weather.sdk.exception.WeatherSDKException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WeatherSDKTest {

    private static final String API_KEY = "test-api-key";
    private static final String CITY_NAME = "London";

    @BeforeEach
    void setUp() throws Exception {
        clearWeatherSDKFactoryInstances();
    }

    @AfterEach
    void tearDown() {
        WeatherSDKFactory.shutdownAll();
    }

    @Test
    void createSDK_ShouldCreateInstanceWithValidParameters() throws WeatherSDKException {
        try (WeatherSDK sdk = WeatherSDKFactory.createSDK(API_KEY, SDKMode.ON_DEMAND)) {
            assertNotNull(sdk);
        }
        assertTrue(WeatherSDKFactory.hasInstance(API_KEY));
    }

    @Test
    void removeSDK_ShouldNotThrowForNonExistentKey() {
        assertDoesNotThrow(() -> WeatherSDKFactory.removeSDK("non-existent-key"));
    }

    @Test
    void getWeather_ShouldThrowExceptionForEmptyCity() throws WeatherSDKException {
        WeatherSDKException exception;
        try (WeatherSDK sdk = WeatherSDKFactory.createSDK(API_KEY, SDKMode.ON_DEMAND)) {
            exception = assertThrows(WeatherSDKException.class,
                    () -> sdk.getWeather(""));
        }

        assertTrue(exception.getMessage().contains("City name cannot be null or empty"));
    }

    @Test
    void getWeather_ShouldThrowExceptionForBlankCity() throws WeatherSDKException {
        WeatherSDKException exception;
        try (WeatherSDK sdk = WeatherSDKFactory.createSDK(API_KEY, SDKMode.ON_DEMAND)) {
            exception = assertThrows(WeatherSDKException.class,
                    () -> sdk.getWeather("   "));
        }

        assertTrue(exception.getMessage().contains("City name cannot be null or empty"));
    }

    @Test
    void getWeather_ShouldThrowExceptionWhenShutdown() throws WeatherSDKException {
        // Given
        WeatherSDK sdk = WeatherSDKFactory.createSDK(API_KEY, SDKMode.ON_DEMAND);
        sdk.close();

        // When & Then
        WeatherSDKException exception = assertThrows(WeatherSDKException.class,
                () -> sdk.getWeather(CITY_NAME));

        assertTrue(exception.getMessage().contains("SDK is shutdown"));
    }

    @Test
    void evictFromCache_ShouldHandleNonExistentCity() throws WeatherSDKException {
        try (WeatherSDK sdk = WeatherSDKFactory.createSDK(API_KEY, SDKMode.ON_DEMAND)) {
            assertDoesNotThrow(() -> sdk.evictFromCache("NonExistentCity"));
        }
    }

    @Test
    void getCacheStats_ShouldReturnValidStatistics() throws WeatherSDKException {
        Map<String, Object> stats;
        try (WeatherSDK sdk = WeatherSDKFactory.createSDK(API_KEY, SDKMode.ON_DEMAND)) {
            stats = sdk.getCacheStats();
        }
        assertNotNull(stats);
        assertEquals(0, stats.get("size"));
        assertEquals(10, stats.get("max_size"));
        assertEquals(SDKMode.ON_DEMAND.toString(), stats.get("mode"));
    }

    @Test
    void getCacheStats_ShouldReturnCorrectSizeForPollingMode() throws WeatherSDKException {
        Map<String, Object> stats;
        try (WeatherSDK sdk = WeatherSDKFactory.createSDK(API_KEY, SDKMode.POLLING)) {
            stats = sdk.getCacheStats();
        }
        assertNotNull(stats);
        assertEquals(SDKMode.POLLING.toString(), stats.get("mode"));
    }

    @Test
    void hasInstance_ShouldReturnCorrectStatus() throws WeatherSDKException {
        boolean beforeCreation = WeatherSDKFactory.hasInstance(API_KEY);
        try (WeatherSDK sdk = WeatherSDKFactory.createSDK(API_KEY, SDKMode.POLLING)) {
            boolean afterCreation = WeatherSDKFactory.hasInstance(API_KEY);
            boolean nonExistent = WeatherSDKFactory.hasInstance("non-existent-key");

            assertFalse(beforeCreation);
            assertTrue(afterCreation);
            assertFalse(nonExistent);
        }
    }

    // Helper method to clear factory instances using reflection
    private void clearWeatherSDKFactoryInstances() throws Exception {
        Field instancesField = WeatherSDKFactory.class.getDeclaredField("instances");
        instancesField.setAccessible(true);
        Map<?, ?> instances = (Map<?, ?>) instancesField.get(null);
        instances.clear();
    }
}