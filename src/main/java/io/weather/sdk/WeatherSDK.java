package io.weather.sdk;

import io.weather.sdk.cache.LRUCache;
import io.weather.sdk.config.RetryConfig;
import io.weather.sdk.config.SDKConfig;
import io.weather.sdk.util.RetryableWeatherFetcher;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.weather.sdk.config.SDKMode;
import io.weather.sdk.exception.*;
import io.weather.sdk.model.*;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author rus.sadykov
 * 05.11.2025
 */
@Slf4j
public class WeatherSDK implements AutoCloseable {
    private static final int MAX_CACHE_SIZE = 10;
    private static final long POLLING_INTERVAL = 5L * 60 * 1000; // 5 minutes
    private static final long HTTP_TIMEOUT_SECONDS = 30;
    private static final String JSON_KEY_WEATHER = "weather";

    private final String apiKey;
    private final SDKMode mode;
    private final SDKConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final LRUCache<String, WeatherData> cache;
    private final ScheduledExecutorService scheduler;
    private final Map<String, ReentrantLock> cityLocks;
    private final Set<CompletableFuture<Void>> pollingTasks;
    private final RetryableWeatherFetcher retryFetcher;
    private volatile boolean isShutdown = false;

    private WeatherSDK(String apiKey, SDKMode mode, SDKConfig config) {
        this.apiKey = Objects.requireNonNull(apiKey, "API key cannot be null");
        this.mode = Objects.requireNonNull(mode, "SDK mode cannot be null");
        this.config = config != null ? config : SDKConfig.defaultConfig();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.cache = new LRUCache<>(MAX_CACHE_SIZE);
        this.cityLocks = new ConcurrentHashMap<>();
        this.pollingTasks = ConcurrentHashMap.newKeySet();
        if (mode == SDKMode.POLLING) {
            this.scheduler = Executors.newSingleThreadScheduledExecutor();
            startPolling();
        } else {
            this.scheduler = null;
        }
        this.retryFetcher = new RetryableWeatherFetcher(config.getRetryConfig());
    }

    /**
     * Returns the current weather data for the given city name in JSON format.
     * If the city name is null or empty, this method throws a WeatherSDKException.
     * If the WeatherSDK instance is shut down, this method throws a WeatherSDKException.
     * The returned JSON string is in the format of the OpenWeatherMap API response.
     * The method first checks the cache for the given city name. If the cache contains
     * fresh weather data for the city, it returns the cached data. Otherwise, it
     * fetches fresh weather data from the OpenWeatherMap API, caches it, and returns
     * the JSON string representation of the fresh weather data.
     *
     * @param cityName the city name to retrieve the weather data for
     * @return the current weather data for the given city name in JSON format
     * @throws WeatherSDKException if the city name is null or empty, or the WeatherSDK instance is shut down
     */
    public String getWeather(String cityName) throws WeatherSDKException {
        validateNotShutdown();

        if (cityName == null || cityName.strip().isBlank()) {
            throw new WeatherSDKException("City name cannot be null or empty");
        }

        String normalizedCityName = normalizeCityName(cityName);
        ReentrantLock lock = cityLocks.computeIfAbsent(normalizedCityName, k -> new ReentrantLock());

        lock.lock();
        try {
            Optional<WeatherData> cachedData = cache.get(normalizedCityName);
            if (cachedData.isPresent() && isDataFresh(cachedData.get())) {
                log.debug("Returning cached weather data for: {}", normalizedCityName);
                return convertToJson(cachedData.get());
            }

            WeatherData freshData = fetchWeatherWithRetry(cityName);
            cache.put(normalizedCityName, freshData);
            return convertToJson(freshData);
        } finally {
            lock.unlock();
        }
    }

    private boolean isDataFresh(WeatherData weatherData) {
        return (System.currentTimeMillis() - weatherData.getLastUpdated()) < config.getCacheTtlMs();
    }

    /**
     * Normalizes a city name by stripping leading/trailing whitespace and converting to lower case.
     * This method is used to normalize city names before caching and retrieving weather data.
     * @param cityName the city name to normalize
     * @return the normalized city name
     */
    private String normalizeCityName(String cityName) {
        return cityName.strip().toLowerCase();
    }

    /**
     * Fetches the current weather data from the OpenWeatherMap API for the given city name.
     * This method first constructs the URL for the API request, then sends a GET request
     * to the API with the constructed URL. If the request is successful, it parses the API
     * response and returns the parsed weather data. If any errors occur during the API
     * request or parsing, this method throws a WeatherSDKException.
     *
     * @param cityName the city name to fetch the weather data for
     * @return the current weather data for the given city name
     * @throws WeatherSDKException if any errors occur during the API request or parsing
     */
    private WeatherData fetchWeatherFromAPI(String cityName) throws WeatherSDKException {
        String url = String.format(
                "https://api.openweathermap.org/data/2.5/weather?q=%s&appid=%s",
                cityName.replace(" ", "%20"),
                apiKey
        );

        return retryFetcher.fetchWithRetry(cityName, () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                return handleApiResponse(response, cityName);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new WeatherSDKException("Thread was interrupted while calling weather API", e);
            } catch (Exception e) {
                throw new WeatherSDKException("Failed to connect to weather API: " + e.getMessage(), e);
            }
        });
    }
}

    private void exponentialBackoff(int retryCount) throws InterruptedException {
        RetryConfig retryConfig = config.getRetryConfig();
        long delayMs = Math.min(
                retryConfig.getBaseDelayMs() * (1L << retryCount),
                retryConfig.getMaxDelayMs()
        );

        double jitter = retryConfig.getJitterFactor() * delayMs * (2 * Math.random() - 1);
        long finalDelayMs = Math.max(100, (long) (delayMs + jitter));

        Thread.sleep(finalDelayMs);
    }

    private WeatherData fetchWeatherWithRetry(String cityName) throws WeatherSDKException {
        int maxRetries = 3;
        int retryCount = 0;

        while (true) {
            try {
                return fetchWeatherFromAPI(cityName);
            } catch (ApiUnavailableException | RateLimitExceededException e) {
                if (retryCount >= maxRetries) {
                    log.warn("Max retries ({}) exceeded for city: {}", maxRetries, cityName);
                    throw e;
                }

                try {
                    exponentialBackoff(retryCount);
                    retryCount++;
                    log.debug("Retry attempt {} for city: {}", retryCount, cityName);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new WeatherSDKException("Retry interrupted for city: " + cityName, ie);
                }
            }
        }
    }

    /**
     * Handles the response from the OpenWeatherMap API.
     *
     * This method takes the HTTP response from the API and the city name associated with the request,
     * and returns the parsed weather data if the response is successful (200 OK).
     * If the response is not successful, it throws a WeatherSDKException with a descriptive error message.
     *
     * The following errors are handled by this method:
     * - 401: Invalid API key
     * - 404: City not found
     * - 429: API rate limit exceeded
     * - 500, 502, 503, 504: Weather service is temporarily unavailable
     * - Any other status code: API request failed
     *
     * @param response the HTTP response from the API
     * @param cityName the city name associated with the request
     * @return the parsed weather data if the response is successful
     * @throws WeatherSDKException if the response is not successful
     */
    private WeatherData handleApiResponse(HttpResponse<String> response, String cityName)
            throws WeatherSDKException {
        int statusCode = response.statusCode();

        return switch (statusCode) {
            case 200 -> parseWeatherResponse(response.body(), cityName);
            case 401 -> throw new InvalidApiKeyException("Invalid API key provided");
            case 404 -> throw new CityNotFoundException("City not found: " + cityName);
            case 429 -> throw new RateLimitExceededException("API rate limit exceeded. Please try again later.");
            case 500, 502, 503, 504 ->
                    throw new ApiUnavailableException("Weather service is temporarily unavailable. Status: " + statusCode);
            default -> throw new WeatherSDKException("API request failed with status: " + statusCode);
        };
    }

    /**
     * Parses the given JSON response from the OpenWeatherMap API into a WeatherData object.
     *
     * This method takes the JSON response from the API and the city name associated with the request,
     * and returns the parsed weather data if the response is valid.
     * If the response is invalid, it throws a WeatherSDKException with a descriptive error message.
     *
     * The following errors are handled by this method:
     * - Invalid JSON: throws a WeatherSDKException with a descriptive error message
     * - Missing weather data: throws a WeatherSDKException with a descriptive error message
     *
     * @param jsonResponse the JSON response from the API
     * @param cityName the city name associated with the request
     * @return the parsed weather data if the response is valid
     * @throws WeatherSDKException if the response is invalid
     */
    private WeatherData parseWeatherResponse(String jsonResponse, String cityName) throws WeatherSDKException {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);

            if (!root.has(JSON_KEY_WEATHER) || !root.get(JSON_KEY_WEATHER).isArray() || root.get(JSON_KEY_WEATHER).isEmpty()) {
                throw new WeatherSDKException("Invalid API response: missing weather data");
            }

            JsonNode weatherNode = root.get(JSON_KEY_WEATHER).get(0);
            Weather weather = new Weather(
                    weatherNode.path("main").asString("Unknown"),
                    weatherNode.path("description").asString("Unknown")
            );

            JsonNode mainNode = root.path("main");
            Temperature temperature = new Temperature(
                    mainNode.path("temp").asDouble(0.0),
                    mainNode.path("feels_like").asDouble(0.0)
            );

            JsonNode windNode = root.path("wind");
            Wind wind = new Wind(
                    windNode.path("speed").asDouble(0.0)
            );

            JsonNode sysNode = root.path("sys");
            Sys sys = new Sys(
                    sysNode.path("sunrise").asLong(0),
                    sysNode.path("sunset").asLong(0)
            );

            return new WeatherData(
                    weather,
                    temperature,
                    root.path("visibility").asInt(0),
                    wind,
                    root.path("dt").asLong(0) * 1000, // Convert to milliseconds
                    sys,
                    root.path("timezone").asInt(0),
                    root.path("name").asString(cityName)
            );
        } catch (Exception e) {
            throw new WeatherSDKException("Failed to parse weather API response", e);
        }
    }

    /**
     * Converts the given WeatherData object to a JSON string.
     *
     * This method takes the given WeatherData object and serializes it into a JSON string.
     * If any errors occur during the serialization, a WeatherSDKException is thrown with a descriptive error message.
     *
     * @param weatherData the WeatherData object to serialize
     * @return the JSON string representation of the given WeatherData object
     * @throws WeatherSDKException if any errors occur during the serialization
     */
    private String convertToJson(WeatherData weatherData) throws WeatherSDKException {
        try {
            return objectMapper.writeValueAsString(weatherData);
        } catch (Exception e) {
            throw new WeatherSDKException("Failed to serialize weather data to JSON", e);
        }
    }

    /**
     * Starts the polling mechanism that periodically updates stale weather data.
     * This method schedules a fixed-rate task with the given polling interval to call
     * {@link #updateStaleWeatherData()}, which updates all stale weather data in the background.
     * If any errors occur during the polling update, the error will be logged.
     */
    private void startPolling() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                updateStaleWeatherData();
            } catch (Exception e) {
                log.error("Error during polling update: {}", e.getMessage(), e);
            }
        }, POLLING_INTERVAL, POLLING_INTERVAL, TimeUnit.MILLISECONDS);
    }

    /**
     * Periodically updates all stale weather data in the background.
     * This method uses the current set of city locks to iterate over all cities that have
     * stale weather data, and schedules a fixed-rate task to update each city's
     * weather data.
     *
     * If any errors occur during the polling update, the error will be logged.
     */
    private void updateStaleWeatherData() {
        List<CompletableFuture<Void>> currentTasks = new ArrayList<>();

        for (String cityName : cityLocks.keySet()) {
            CompletableFuture<Void> task = CompletableFuture.runAsync(() -> updateSingleCity(cityName));
            currentTasks.add(task);
        }

        pollingTasks.addAll(currentTasks);

        CompletableFuture.allOf(currentTasks.toArray(new CompletableFuture[0]))
                .whenComplete((result, throwable) -> {
                    pollingTasks.removeAll(new HashSet<>(currentTasks));
                    if (throwable != null) {
                        log.error("Completed polling update with errors for {} cities", currentTasks.size(), throwable);
                    } else {
                        log.debug("Completed polling update with {} cities", currentTasks.size());
                    }
                });
    }

    /**
     * Updates the weather data for a single city in the background.
     * This method takes a city name and uses the associated city lock to
     * iterate over the cache and check if the weather data for the city
     * is stale. If the weather data is stale, it fetches fresh weather
     * data from the OpenWeatherMap API and updates the cache with the fresh
     * data.
     *
     * If any errors occur during the polling update, the error will be
     * logged.
     *
     * @param cityName the city name to update the weather data for
     */
    private void updateSingleCity(String cityName) {
        ReentrantLock lock = cityLocks.get(cityName);
        if (lock == null || !lock.tryLock()) {
            return;
        }

        try {
            Optional<WeatherData> cachedData = cache.get(cityName);
            if (cachedData.isPresent() && cachedData.get().isDataStale()) {
                try {
                    WeatherData freshData = fetchWeatherFromAPI(cityName);
                    cache.put(cityName, freshData);
                    log.debug("Polling update: refreshed weather data for {}", cityName);
                } catch (Exception e) {
                    log.warn("Polling update: failed to refresh weather for {}: {}", cityName, e.getMessage());
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Validates that the SDK has not been shut down.
     *
     * This method checks if the SDK has been shut down and throws a WeatherSDKException
     * if it has. This is used to prevent any requests from being processed after the
     * SDK has been shut down.
     *
     * @throws WeatherSDKException if the SDK has been shut down
     */
    private void validateNotShutdown() throws WeatherSDKException {
        if (isShutdown) {
            throw new WeatherSDKException("SDK is shutdown and cannot process requests");
        }
    }

    /**
     * Returns a map containing the current cache statistics.
     *
     * The returned map contains the following statistics:
     * <ul>
     * <li>size: the current number of entries in the cache</li>
     * <li>max_size: the maximum allowed number of entries in the cache</li>
     * <li>mode: the current cache mode (e.g. polling, on-demand)</li>
     * <li>active_polling_tasks: the number of active polling tasks running in the background</li>
     * </ul>
     *
     * @return a map containing the current cache statistics
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("size", cache.size());
        stats.put("max_size", config.getCacheSize());
        stats.put("mode", mode.toString());
        stats.put("active_polling_tasks", pollingTasks.size());
        stats.put("cache_ttl_ms", config.getCacheTtlMs());
        stats.put("polling_interval_ms", config.getPollingIntervalMs());
        return stats;
    }

    /**
     * Removes the given city name from the cache and the city locks.
     *
     * This method takes a city name and removes the corresponding weather data from the cache
     * and the city lock. This method is thread-safe and does not throw any checked or unchecked
     * exceptions.
     *
     * @param cityName the city name to remove from the cache and city locks
     */
    public void evictFromCache(String cityName) {
        if (cityName != null) {
            String normalizedName = normalizeCityName(cityName);
            cache.remove(normalizedName);
            cityLocks.remove(normalizedName);
        }
    }

    /**
     * Clears the cache and removes all city locks.
     * This method is thread-safe and does not throw any checked or unchecked exceptions.
     */
    public void clearCache() {
        cache.clear();
        cityLocks.clear();
    }

    /**
     * Closes the WeatherSDK instance and stops all background polling tasks.
     * This method is thread-safe and does not throw any checked or unchecked exceptions.
     *
     * After calling this method, all WeatherSDK instances will be closed and
     * the factory will be reset to its initial state.
     */
    @Override
    public void close() {
        isShutdown = true;

        for (CompletableFuture<Void> task : pollingTasks) {
            task.cancel(true);
        }
        pollingTasks.clear();

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException _) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        cache.clear();
        cityLocks.clear();
    }

    /**
     * Creates a new WeatherSDK instance with the given API key and mode.
     * If the API key is null or empty, this method throws a WeatherSDKException.
     * If the SDK mode is null, this method throws a WeatherSDKException.
     *
     * @param apiKey the API key to use for the SDK instance
     * @param mode the mode of the SDK instance
     * @return the created SDK instance
     * @throws WeatherSDKException if the API key is null or empty, or the SDK mode is null
     */
    static WeatherSDK createInstance(String apiKey, SDKMode mode, SDKConfig config) throws WeatherSDKException {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new InvalidApiKeyException("API key cannot be null or empty");
        }
        if (mode == null) {
            throw new WeatherSDKException("SDK mode cannot be null");
        }
        return new WeatherSDK(apiKey.trim(), mode, config);
    }
}
