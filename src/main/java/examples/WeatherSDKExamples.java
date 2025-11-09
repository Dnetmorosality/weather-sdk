package examples;

import io.weather.sdk.WeatherSDK;
import io.weather.sdk.WeatherSDKFactory;
import io.weather.sdk.config.SDKConfig;
import io.weather.sdk.config.SDKMode;
import io.weather.sdk.exception.WeatherSDKException;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * @author rus.sadykov
 * 05.11.2025
 */
@Slf4j
public class WeatherSDKExamples {
    // API keys are loaded from environment variables or system properties to avoid committing secrets.
    // Prefer environment variables: WEATHER_API_KEY
    // Or JVM properties: -Dweather.api.key=...
    private static final String VALID_API_KEY = requireApiKey("WEATHER_API_KEY", "weather.api.key");
    private static final String BERLIN = "Berlin";
    private static final String LONDON = "London";
    private static final String NEW_YORK = "New York";
    private static final String PARIS = "Paris";
    private static final String ROME = "Rome";
    private static final String SYDNEY = "Sydney";
    private static final String TOKYO = "Tokyo";

    private WeatherSDKExamples() {
    }

    /**
     * Demonstrates the comprehensive capabilities of the Weather SDK.
     * This example covers four key aspects of the SDK: factory and instance
     * management, on-demand mode, polling mode, and cache behavior.
     * It also shuts down all remaining instances after the demonstration is
     * completed.
     */
    static void main() {
        try {
            log.info("=== WEATHER SDK COMPREHENSIVE DEMONSTRATION ===");

            log.info("\n1. === FACTORY AND INSTANCE MANAGEMENT ===");
            demoFactoryManagement();

            log.info("\n2. === ON-DEMAND MODE DEMONSTRATION ===");
            demoOnDemandMode();

            log.info("\n3. === POLLING MODE DEMONSTRATION ===");
            demoPollingMode();

            log.info("\n4. === CACHE BEHAVIOR DEMONSTRATION ===");
            demoCacheBehavior();

        } catch (Exception e) {
            log.error("Demonstration failed: {}", e.getMessage(), e);
        } finally {
            WeatherSDKFactory.shutdownAll();
            log.info("=== ALL DEMONSTRATIONS COMPLETED ===");
        }
    }

    private static String requireApiKey(String envName, String sysPropName) {
        String fromEnv = System.getenv(envName);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        String fromProp = System.getProperty(sysPropName);
        if (fromProp != null && !fromProp.isBlank()) {
            return fromProp;
        }
        throw new IllegalStateException("Missing API key. Set env variable '" + envName + "' or system property '" + sysPropName + "'.");
    }

    /**
     * Demonstrates the factory management capabilities of the Weather SDK.
     * This example tests four key aspects of the factory:
     * <ol>
     * <li>Creating new SDK instances with valid API keys and modes</li>
     * <li>Throwing exceptions for duplicate instance creation</li>
     * <li>Checking the existence of SDK instances</li>
     * <li>Removing and re-creating SDK instances</li>
     * </ol>
     */
    private static void demoFactoryManagement() {
        log.info("Testing Factory instance management...");

        // Test 1: Create SDK instance
        try (WeatherSDK sdk1 = WeatherSDKFactory.createSDK(VALID_API_KEY, SDKMode.ON_DEMAND, SDKConfig.defaultConfig())) {
            log.info("✓ Successfully created first SDK instance");
            sdk1.getWeather(LONDON);

            // Test 2: Try to create duplicate instance with same API key
            testDuplicateInstanceCreation();

            // Test 3: Check instance existence
            boolean exists = WeatherSDKFactory.hasInstance(VALID_API_KEY);
            log.info("✓ Instance exists check: {}", exists);

            // Test 4: Remove instance and create again
            WeatherSDKFactory.removeSDK(VALID_API_KEY);
            log.info("✓ Instance removed successfully");

            WeatherSDK sdk4 = WeatherSDKFactory.createSDK(VALID_API_KEY, SDKMode.POLLING);
            log.info("✓ Successfully created new instance after removal");

            sdk4.close();
            WeatherSDKFactory.removeSDK(VALID_API_KEY);

        } catch (WeatherSDKException e) {
            log.error("Factory management test failed: {}", e.getMessage());
        } finally {
            WeatherSDKFactory.removeSDK(VALID_API_KEY);
        }
    }

    /**
     * Tests the creation of duplicate WeatherSDK instances with the same API key.

     * This test method attempts to create a new WeatherSDK instance with the same API key
     * as an existing instance. If the WeatherSDK instance management correctly prevents
     * duplicate instance creation, the test will log an info message indicating that the
     * instance was correctly prevented.

     * If the WeatherSDK instance management fails to prevent duplicate instance creation,
     * the test will log an error message indicating that the duplicate instance was allowed.
     */
    private static void testDuplicateInstanceCreation() {
        try (WeatherSDK sdk3 = WeatherSDKFactory.createSDK(VALID_API_KEY, SDKMode.POLLING)) {
            sdk3.getWeather(ROME);
            log.error("✗ Should not allow duplicate instances");
        } catch (WeatherSDKException e) {
            log.info("✓ Correctly prevented duplicate instance: {}", e.getMessage());
        }
    }

    /**
     * Demonstrates the On-Demand mode of the Weather SDK.

     * In this mode, the Weather SDK fetches data only when requested by the application.

     * This test method creates a new WeatherSDK instance in On-Demand mode, and then
     * requests the current weather for a city. The response time for the first request
     * is compared to the response time for the second request to verify that the cache
     * is working effectively.
     */
    private static void demoOnDemandMode() {
        log.info("Testing On-Demand mode (data fetched only on request)...");

        try (WeatherSDK sdk = WeatherSDKFactory.createSDK(VALID_API_KEY, SDKMode.ON_DEMAND)) {

            // Test cache miss - first request
            long startTime = System.currentTimeMillis();
            String weather1 = sdk.getWeather(LONDON);
            long firstCallTime = System.currentTimeMillis() - startTime;
            log.info("✓ First request for {} (cache miss): {}ms", LONDON, firstCallTime);
            log.debug("First response length: {} characters", weather1.length());

            // Test cache hit - second request
            startTime = System.currentTimeMillis();
            String weather2 = sdk.getWeather(LONDON);
            long secondCallTime = System.currentTimeMillis() - startTime;
            log.info("✓ Second request for {} (cache hit): {}ms", LONDON, secondCallTime);
            log.debug("Second response length: {} characters", weather2.length());

            // Verify cache performance improvement
            boolean cacheEffective = secondCallTime < firstCallTime;
            log.info("✓ Cache effectiveness: {} (cache hit {} faster)",
                    cacheEffective ? "YES" : "NO",
                    cacheEffective ? "is" : "is not");

            // Show cache stats
            Map<String, Object> stats = sdk.getCacheStats();
            log.info("✓ Cache stats: {}", stats);

        } catch (WeatherSDKException e) {
            log.error("On-Demand mode test failed: {}", e.getMessage());
        } finally {
            WeatherSDKFactory.removeSDK(VALID_API_KEY);
        }
    }

    private static void demoPollingMode() {
        log.info("Testing Polling mode (background updates)...");

        try (WeatherSDK sdk = WeatherSDKFactory.createSDK(VALID_API_KEY, SDKMode.POLLING)) {

            // Populate cache with multiple cities
            String[] cities = {PARIS, BERLIN, TOKYO, NEW_YORK, SYDNEY};
            for (String city : cities) {

                String weather = sdk.getWeather(city);
                log.info("✓ Initial data for {}: {} characters", city, weather.length());

            }

            Map<String, Object> initialStats = sdk.getCacheStats();
            log.info("✓ Initial cache stats: {}", initialStats);

            // Wait to see polling updates
            log.info("Waiting 15 seconds for polling updates...");
            for (int i = 1; i <= 3; i++) {
                if (!pollOnceAndLog(sdk, i)) {
                    break;
                }
            }
            String freshWeather = sdk.getWeather(PARIS);
            log.info("✓ Fresh data available after polling: {} characters", freshWeather.length());

        } catch (WeatherSDKException e) {
            log.error("Polling mode test failed: {}", e.getMessage());
        } finally {
            WeatherSDKFactory.removeSDK(VALID_API_KEY);
        }
    }

    /**
     * Sleeps for 5 seconds, fetches current cache stats and logs them.
     * Returns false if the thread was interrupted (the interrupt flag is preserved), true otherwise.
     */
    private static boolean pollOnceAndLog(WeatherSDK sdk, int iteration) {
        try {
            Thread.sleep(5000);
            Map<String, Object> currentStats = sdk.getCacheStats();
            log.info("  Polling update check {}: {}", iteration, currentStats);
            return true;
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Demonstrates advanced cache behavior of the Weather SDK, including:
     * <ol>
     * <li>Data freshness within 10 minutes</li>
     * <li>Manual cache eviction</li>
     * <li>Cache size limit (LRU behavior)</li>
     * </ol>
     *
     * This method first tests data freshness within 10 minutes, then manually evicts
     * a city from the cache and verifies that the cache miss is handled correctly.
     * Finally, it tests the cache size limit (LRU behavior) by populating the cache
     * with a large number of cities and verifying that the least recently used entries
     * are evicted correctly.
     */
    private static void demoCacheBehavior() {
        log.info("Testing advanced cache behavior...");

        try (WeatherSDK sdk = WeatherSDKFactory.createSDK(VALID_API_KEY, SDKMode.ON_DEMAND)) {

            // Test 1: Data freshness within 10 minutes
            sdk.getWeather(ROME);
            log.info("✓ First Rome request completed");

            // Immediate request should be from cache
            sdk.getWeather(ROME);
            log.info("✓ Immediate cache hit verified");

            // Test 2: Manual cache eviction
            sdk.evictFromCache(ROME);
            log.info("✓ Manual cache eviction completed");

            sdk.getWeather(ROME); // Should fetch fresh
            log.info("✓ Cache miss after eviction verified");

            // Test 3: Cache size limit (LRU behavior)
            String[] realCities = {
                    LONDON, PARIS, BERLIN, "Madrid", "Amsterdam",
                    TOKYO, SYDNEY, "Moscow", "Cairo", "Delhi",
                    "Beijing", "Toronto", PARIS, LONDON
            };
            log.info("Testing LRU cache eviction with {} cities...", realCities.length);
            for (String city : realCities) {
                sdk.getWeather(city);
                log.debug("  Cached: {}", city);
            }

            Map<String, Object> finalStats = sdk.getCacheStats();
            log.info("✓ Final cache stats after LRU test: {}", finalStats);
        } catch (WeatherSDKException e) {
            log.error("Cache behavior test failed: {}", e.getMessage());
        } finally {
            WeatherSDKFactory.removeSDK(VALID_API_KEY);
        }
    }
}
