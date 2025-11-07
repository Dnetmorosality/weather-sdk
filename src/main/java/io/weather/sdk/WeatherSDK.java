package io.weather.sdk;

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
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, WeatherData> cache;
    private final ScheduledExecutorService scheduler;
    private final Map<String, ReentrantLock> cityLocks;
    private volatile boolean isShutdown = false;

    private WeatherSDK(String apiKey, SDKMode mode) {
        this.apiKey = apiKey;
        this.mode = mode;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.cache = new ConcurrentHashMap<>();
        this.cityLocks = new ConcurrentHashMap<>();

        if (mode == SDKMode.POLLING) {
            this.scheduler = Executors.newScheduledThreadPool(1);
            startPolling();
        } else {
            this.scheduler = null;
        }
    }

    /**
     * Returns the current weather for the given city name.
     * If the city name is already cached and the data is fresh, it will return the cached data.
     * Otherwise, it will fetch the weather from the API and update the cache.
     *
     * @param cityName the name of the city
     * @return the JSON representation of the current weather
     * @throws WeatherSDKException if the city name is invalid or the API request fails
     */
    public String getWeather(String cityName) throws WeatherSDKException {
        validateNotShutdown();

        if (cityName == null || cityName.strip().isBlank()) {
            throw new WeatherSDKException("City name cannot be null or empty");
        }

        String normalizedCityName = cityName.strip().toLowerCase();
        ReentrantLock lock = cityLocks.computeIfAbsent(normalizedCityName, k -> new ReentrantLock());

        lock.lock();
        try {
            WeatherData cachedData = cache.get(normalizedCityName);
            if (cachedData != null && cachedData.isDataFresh()) {
                return convertToJson(cachedData);
            }

            WeatherData freshData = fetchWeatherFromAPI(cityName);
            updateCache(normalizedCityName, freshData);
            return convertToJson(freshData);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Fetches the current weather for the given city name from the OpenWeatherMap API.
     *
     * @param cityName the name of the city
     * @return the current weather data for the given city name
     * @throws WeatherSDKException if the API request fails or the city name is invalid
     */
    private WeatherData fetchWeatherFromAPI(String cityName) throws WeatherSDKException {
        String url = String.format(
                "https://api.openweathermap.org/data/2.5/weather?q=%s&appid=%s",
                cityName.replace(" ", "%20"),
                apiKey
        );

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
        } catch (WeatherSDKException e) {
            throw e;
        } catch (Exception e) {
            throw new WeatherSDKException("Failed to connect to weather API: " + e.getMessage(), e);
        }
    }

    /**
     * Handles the response from the weather API and returns the parsed weather data.
     * If the response status code indicates an error, it will throw a WeatherSDKException
     * with a corresponding error message.
     *
     * @param response the response from the weather API
     * @param cityName the name of the city
     * @return the parsed weather data
     * @throws WeatherSDKException if the API request failed
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
     * Parses the JSON response from the weather API into a WeatherData object.
     * If the response is invalid (missing weather data), it will throw a WeatherSDKException.
     * If there is an error while parsing the JSON, it will throw a WeatherSDKException with a corresponding error message.
     *
     * @param jsonResponse the JSON response from the weather API
     * @param cityName the name of the city
     * @return the parsed weather data
     * @throws WeatherSDKException if the API request failed or the response is invalid
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
            Wind wind = new Wind(windNode.path("speed").asDouble(0.0));

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
                    root.path("dt").asLong(0),
                    sys,
                    root.path("timezone").asInt(0),
                    root.path("name").asString(cityName)
            );
        } catch (Exception e) {
            throw new WeatherSDKException("Failed to parse weather API response", e);
        }
    }

    /**
     * Converts the given weather data to a JSON string.
     *
     * @param weatherData the weather data to be converted
     * @return the JSON string representation of the weather data
     * @throws WeatherSDKException if the weather data cannot be converted to JSON
     */
    private String convertToJson(WeatherData weatherData) throws WeatherSDKException {
        try {
            return objectMapper.writeValueAsString(weatherData);
        } catch (Exception e) {
            throw new WeatherSDKException("Failed to serialize weather data to JSON", e);
        }
    }

    /**
     * Updates the cache with the given weather data. If the cache is full, it will remove the oldest cached city.
     * @param cityName the name of the city
     * @param data the weather data for the given city
     */
    private void updateCache(String cityName, WeatherData data) {
        if (cache.size() >= MAX_CACHE_SIZE) {
            String oldestCity = findOldestCachedCity();
            if (oldestCity != null) {
                cache.remove(oldestCity);
                cityLocks.remove(oldestCity);
            }
        }
        cache.put(cityName, data);
    }

    /**
     * Finds the oldest cached city in the cache. If the cache is empty, it will return null.
     * @return the name of the oldest cached city, or null if the cache is empty
     */
    private String findOldestCachedCity() {
        return cache.entrySet().stream()
                .min(Map.Entry.comparingByValue(Comparator.comparingLong(WeatherData::getLastUpdated)))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * Starts the polling mechanism for updating stale weather data in the cache.
     * It will schedule a task to run at a fixed rate of {@link #POLLING_INTERVAL} milliseconds,
     * which will update all stale weather data in the cache by fetching the latest weather data from the API.
     * If there is an error while updating the stale weather data, it will log an error message.
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
     * Updates the stale weather data in the cache by fetching the latest weather data from the API.
     * It will iterate over the cache and find all the entries that are stale (i.e. their data is older than the cache TTL).
     * For each stale entry, it will fetch the latest weather data from the API and update the cache.
     * If there is an error while fetching the weather data, it will log an error message.
     */
    private void updateStaleWeatherData() {
        cache.entrySet().stream()
                .filter(entry -> entry.getValue().isDataStale())
                .forEach(entry -> {
                    String cityName = entry.getKey();
                    try {
                        WeatherData freshData = fetchWeatherFromAPI(cityName);
                        cache.put(cityName, freshData);
                        log.info("Updated weather data for: {}", cityName);
                    } catch (Exception e) {
                        log.error("Failed to update weather for {}: {}", cityName, e.getMessage());
                    }
                });
    }

    /**
     * Validates that the SDK is not shutdown before processing a request.
     * If the SDK is shutdown, it will throw a WeatherSDKException.
     * @throws WeatherSDKException if the SDK is shutdown
     */
    private void validateNotShutdown() throws WeatherSDKException {
        if (isShutdown) {
            throw new WeatherSDKException("SDK is shutdown and cannot process requests");
        }
    }

    /**
     * Returns a map of statistics about the cache, including the current size, maximum allowed size, the set of cached cities, and the SDK mode.
     *
     * @return a map of cache statistics
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("size", cache.size());
        stats.put("max_size", MAX_CACHE_SIZE);
        stats.put("cached_cities", cache.keySet());
        stats.put("mode", mode.toString());
        return stats;
    }

    /**
     * Closes the SDK and releases all associated resources.
     * After calling this method, the SDK will not be able to process any requests.
     * It will shut down the scheduler and clear the cache and city locks.
     */
    @Override
    public void close() {
        isShutdown = true;

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
     * Creates a new instance of the WeatherSDK.
     * @param apiKey the API key to use for requests to the weather service
     * @param mode the SDK mode to use for requests to the weather service
     * @return a new instance of the WeatherSDK
     * @throws WeatherSDKException if the API key is null or empty, or the SDK mode is null
     */
    static WeatherSDK createInstance(String apiKey, SDKMode mode) throws WeatherSDKException {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new InvalidApiKeyException("API key cannot be null or empty");
        }
        if (mode == null) {
            throw new WeatherSDKException("SDK mode cannot be null");
        }
        return new WeatherSDK(apiKey.trim(), mode);
    }
}
