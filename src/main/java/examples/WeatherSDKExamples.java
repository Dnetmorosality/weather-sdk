package examples;

import io.weather.sdk.WeatherSDK;
import io.weather.sdk.WeatherSDKFactory;
import io.weather.sdk.config.SDKMode;
import io.weather.sdk.exception.CityNotFoundException;
import io.weather.sdk.exception.WeatherSDKException;
import lombok.extern.slf4j.Slf4j;

/**
 * @author rus.sadykov
 * 05.11.2025
 */
@Slf4j
class WeatherSDKExamples {
    private static final String API_KEY = "api_key";
    private WeatherSDKExamples() {}
    static void main() {
        try {
            log.info("=== ON-DEMAND MODE EXAMPLE ===");
            demoOnDemandMode();

            log.info("=== POLLING MODE EXAMPLE ===");
            demoPollingMode();

            log.info("=== ERROR HANDLING EXAMPLE ===");
            demoErrorHandling();

            log.info("=== CACHE DEMONSTRATION ===");
            demoCache();

        } catch (Exception e) {
            log.error("Example failed: {}", e.getMessage(), e);
        } finally {
            WeatherSDKFactory.shutdownAll();
        }
    }

    private static void demoOnDemandMode() {
        try (WeatherSDK sdk = WeatherSDKFactory.createSDK(API_KEY, SDKMode.ON_DEMAND)) {
            String weatherJson = sdk.getWeather("Zocca");
            log.info("Weather data for Zocca:");
            log.debug("{}", weatherJson);

            // Show cache stats
            log.info("Cache stats: {}", sdk.getCacheStats());

        } catch (WeatherSDKException e) {
            log.error("Error: {}", e.getMessage(), e);
        } finally {
            WeatherSDKFactory.removeSDK(API_KEY);
        }
    }

    private static void demoPollingMode() {
        try (WeatherSDK sdk = WeatherSDKFactory.createSDK(API_KEY, SDKMode.POLLING)) {
            // Get weather for multiple cities
            String[] cities = {"Paris", "Berlin", "Tokyo"};

            for (String city : cities) {
                String weatherJson = sdk.getWeather(city);
                log.info("Weather data for {}:", city);
                log.info("{} characters of JSON data", weatherJson.length());
            }

            log.info("Cache stats: {}", sdk.getCacheStats());

            log.info("Waiting 10 seconds to demonstrate polling...");
            Thread.sleep(10000);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting during polling demo");
        } catch (WeatherSDKException e) {
            log.error("Error: {}", e.getMessage(), e);
        } finally {
            WeatherSDKFactory.removeSDK(API_KEY);
        }
    }

    private static void demoErrorHandling() {
        try (WeatherSDK sdk = WeatherSDKFactory.createSDK(API_KEY, SDKMode.ON_DEMAND)) {
            // Invalid city name
            sdk.getWeather("InvalidCityNameThatDoesNotExist");
        } catch (CityNotFoundException e) {
            log.warn("Properly handled city not found: {}", e.getMessage());
        } catch (WeatherSDKException e) {
            log.warn("Properly handled SDK exception: {}", e.getMessage());
        }

        try (WeatherSDK sdk = WeatherSDKFactory.createSDK(API_KEY, SDKMode.ON_DEMAND)) {
            // Empty city name
            sdk.getWeather("");
        } catch (WeatherSDKException e) {
            log.warn("Properly handled validation error: {}", e.getMessage());
        } finally {
            WeatherSDKFactory.removeSDK(API_KEY);
        }
    }

    private static void demoCache() {
        try (WeatherSDK sdk = WeatherSDKFactory.createSDK(API_KEY, SDKMode.ON_DEMAND)) {
            long startTime = System.currentTimeMillis();
            sdk.getWeather("Rome");
            long firstCallTime = System.currentTimeMillis() - startTime;

            startTime = System.currentTimeMillis();
            sdk.getWeather("Rome");
            long secondCallTime = System.currentTimeMillis() - startTime;

            log.info("First call time: {}ms", firstCallTime);
            log.info("Second call time: {}ms", secondCallTime);
            log.info("Cache hit demonstrated: {}", (secondCallTime < firstCallTime));
            log.info("Cache stats: {}", sdk.getCacheStats());

        } catch (WeatherSDKException e) {
            log.error("Error: {}", e.getMessage(), e);
        } finally {
            WeatherSDKFactory.removeSDK(API_KEY);
        }
    }
}
