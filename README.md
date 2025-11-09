<div>
  <a href="https://github.com/Dnetmorosality/weather-sdk/stargazers" target="_blank">
    <img src="https://img.shields.io/github/stars/Dnetmorosality/weather-sdk?style=social" alt="Stars earned"/>
  </a>
  <a href="https://github.com/Dnetmorosality/weather-sdk/forks" target="_blank">
    <img src="https://img.shields.io/github/forks/Dnetmorosality/weather-sdk?style=social" alt="Forks"/>
  </a>
  <a href="https://github.com/Dnetmorosality/weather-sdk/discussions" target="_blank">
    <img src="https://img.shields.io/github/discussions/Dnetmorosality/weather-sdk?label=welcome%20to%20discussions&logo=github&style=social" alt="discussions"/>
  </a>
</div>

# Weather SDK

A high-performance Java SDK for weather data with intelligent caching, retry mechanisms, and multiple operation modes. Built for reliability and efficiency in production environments.

[![tag](https://img.shields.io/github/v/tag/Dnetmorosality/weather-sdk?style=flat&include_prereleases&logo=github)](https://github.com/Dnetmorosality/weather-sdk/tags) [![release](https://img.shields.io/github/v/release/Dnetmorosality/weather-sdk?style=flat&include_prereleases&logo=github)](https://github.com/Dnetmorosality/weather-sdk/releases)

[![last_commit](https://img.shields.io/github/last-commit/Dnetmorosality/weather-sdk?style=flat&logo=github)](https://github.com/Dnetmorosality/weather-sdk/commits) [![release_date](https://img.shields.io/github/release-date/Dnetmorosality/weather-sdk?style=flat&logo=github)](https://github.com/Dnetmorosality/weather-sdk/releases)

[![license](https://img.shields.io/github/license/Dnetmorosality/weather-sdk?style=flat)](LICENSE) [![java](https://img.shields.io/badge/Java-17%2B-important?style=flat&logo=java)](https://openjdk.org/)

![repo_size](https://img.shields.io/github/repo-size/Dnetmorosality/weather-sdk?style=flat&logo=github) ![languages_code_size](https://img.shields.io/github/languages/code-size/Dnetmorosality/weather-sdk?style=flat&logo=github) ![languages_count](https://img.shields.io/github/languages/count/Dnetmorosality/weather-sdk?style=flat&logo=github) ![languages_top](https://img.shields.io/github/languages/top/Dnetmorosality/weather-sdk?style=flat&logo=github)

## 📇 Table of Contents

- [About](#-about-)
- [Demo](#-demo-)
- [Features](#-features-)
- [Getting Started](#-getting-started-)
- [Authors](#-authors-)
- [Licensing](#-licensing-)

## 📖 About

Weather SDK is a robust Java library that provides seamless access to weather data through the OpenWeatherMap API. Designed with enterprise requirements in mind, it offers intelligent caching, configurable retry mechanisms, and multiple operation modes to suit various use cases.

### Motivation

I didn't have time for my own projects, but then I was offered an interesting task.

## 📸 Demo

This demonstration shows the SDK
<img src="https://https://github.com/Dnetmorosality/weather-sdk/tree/develop/src/main/java/examples/demo.gif?raw=true" width="100%"/>

## 🎚 Features

### Core Capabilities
- **Dual Operation Modes**: Choose between On-Demand (fetch when needed) and Polling (background updates)
- **Intelligent Caching**: LRU cache with configurable TTL and size limits
- **Retry Mechanism**: Exponential backoff with jitter for handling transient failures
- **Thread-Safe Design**: Built for concurrent environments

### Advanced Features
- **Factory Pattern**: Centralized instance management with lifecycle control
- **Comprehensive Error Handling**: Specific exceptions for different error scenarios
- **Performance Monitoring**: Built-in cache statistics and performance metrics
- **Resource Management**: Auto-closeable interfaces for proper resource cleanup

### Operation Modes
- **On-Demand Mode**: Fetch data only when requested, perfect for low-frequency applications
- **Polling Mode**: Automatic background updates, ideal for real-time applications

## 🚦 Getting Started

### Prerequisites

- **Java 25+** - Ensure you have Java 25 or later installed
- **OpenWeatherMap API Key** - Register at [OpenWeatherMap](https://openweathermap.org) to get your API key

### Building from Source

1. **Clone the repository**:
```bash
git clone https://github.com/Dnetmorosality/weather-sdk.git
cd weather-sdk
```
2. **Build the project**:
```bash
mvn clean install
```
3. **Add dependency to your project's pom.xml:
```xml
 <dependency>
    <groupId>io.weather</groupId>
    <artifactId>weather-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```
4. **Add API Key**:
   API keys are loaded from environment variables or system properties to avoid committing secrets.
   Prefer environment variables: WEATHER_API_KEY
   Or JVM properties: -Dweather.api.key=...

### Basic Usage
```java
import io.weather.sdk.WeatherSDK;
import io.weather.sdk.WeatherSDKFactory;
import io.weather.sdk.config.SDKMode;
import io.weather.sdk.exception.WeatherSDKException;

import java.util.Map;

public class Main {
    static void main() {
        String apiKey = System.getenv("WEATHER_API_KEY");

        try (WeatherSDK sdk = WeatherSDKFactory.createSDK(apiKey, SDKMode.ON_DEMAND, null)) {

            // Get weather data for a city
            String weatherJson = sdk.getWeather("London");
            System.out.println("Weather data: " + weatherJson);

            // Get cache statistics
            Map<String, Object> stats = sdk.getCacheStats();
            System.out.println("Cache stats: " + stats);

        } catch (WeatherSDKException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
```
### ⚙️ Configuration
When creating an SDK instance, you can pass your API configuration
```java
SDKConfig config = SDKConfig.builder()
                .cacheSize(50)                    // Maximum cities to cache
                .cacheTtlMs(300_000)             // 5 minutes cache validity
                .pollingIntervalMs(600_000)      // 10 minutes for polling mode
                .httpTimeout(Duration.ofSeconds(30))
                .retryConfig(RetryConfig.builder()
                        .maxRetries(5)               // Maximum retry attempts
                        .baseDelayMs(1000)           // Base delay for exponential backoff
                        .maxDelayMs(30000)           // Maximum delay between retries
                        .jitterFactor(0.3)           // Jitter to avoid thundering herd
                        .build())
                .build();
WeatherSDK sdk = WeatherSDKFactory.createSDK(apiKey, SDKMode.ON_DEMAND, config);
```

## 📚 API Reference

### Core Methods

| Method | Description | Parameters | Returns |
|--------|-------------|------------|---------|
| `getWeather(String city)` | Get weather data for city | `city`: City name | JSON string |
| `getCacheStats()` | Get cache statistics | None | `Map<String, Object>` |
| `evictFromCache(String city)` | Remove city from cache | `city`: City name | void |
| `close()` | Release resources | None | void |

### Factory Methods

| Method | Description | Parameters | Returns |
|--------|-------------|------------|---------|
| `createSDK(apiKey, mode)` | Create SDK instance | `apiKey`: API key, `mode`: Operation mode | `WeatherSDK` |
| `createSDK(apiKey, mode, config)` | Create configured SDK | `apiKey`: API key, `mode`: Operation mode, `config`: SDK configuration | `WeatherSDK` |
| `removeSDK(apiKey)` | Remove SDK instance | `apiKey`: API key | void |
| `hasInstance(apiKey)` | Check instance exists | `apiKey`: API key | `boolean` |
| `shutdownAll()` | Shutdown all instances | None | void |

### Configuration Classes

#### SDKConfig Builder Methods

| Method | Description | Default |
|--------|-------------|---------|
| `.cacheSize(int size)` | Maximum cache entries | 10 |
| `.cacheTtlMs(long ms)` | Cache time-to-live in milliseconds | 600,000 (10 min) |
| `.pollingIntervalMs(long ms)` | Polling interval in milliseconds | 300,000 (5 min) |
| `.httpTimeout(Duration timeout)` | HTTP request timeout | 30 seconds |
| `.retryConfig(RetryConfig config)` | Retry configuration | Default retry config |

#### RetryConfig Builder Methods

| Method | Description | Default |
|--------|-------------|---------|
| `.maxRetries(int retries)` | Maximum retry attempts | 3 |
| `.baseDelayMs(long ms)` | Base delay for exponential backoff | 1,000 ms |
| `.maxDelayMs(long ms)` | Maximum delay between retries | 30,000 ms |
| `.jitterFactor(double factor)` | Jitter factor (0.0 - 1.0) | 0.3 |

## ⚠️ Exception Types

### Checked Exceptions

| Exception | Description | Common Causes | Recommended Action |
|-----------|-------------|---------------|-------------------|
| `CityNotFoundException` | Requested city not found | • Invalid city name<br>• Typo in city name<br>• City not in database | • Verify city spelling<br>• Check city exists in OpenWeatherMap<br>• Use different city name |
| `RateLimitExceededException` | API rate limit exceeded | • Too many requests<br>• Free tier limits<br>• Concurrent requests | • Implement request throttling<br>• Upgrade API plan<br>• Use caching effectively |
| `ApiUnavailableException` | Weather service unavailable | • OpenWeatherMap API down<br>• Network issues<br>• Server errors (5xx) | • Retry with exponential backoff<br>• Check API status page<br>• Implement fallback mechanism |
| `InvalidApiKeyException` | Invalid or missing API key | • Wrong API key<br>• Key not activated<br>• Key expired | • Verify API key correctness<br>• Activate key in OpenWeatherMap<br>• Generate new API key |
| `WeatherSDKException` | General SDK error | • Network timeouts<br>• JSON parsing errors<br>• Unknown errors | • Check network connectivity<br>• Verify response format<br>• Contact support |

## ©️ Authors
* **Rustam Sadykov** - *Initial work* - [dnetmorosality](github.com/Dnetmorosality).

## 🔏 Licensing
This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.