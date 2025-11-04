# Weather SDK for OpenWeatherMap API

A Java SDK for accessing weather data from OpenWeatherMap API with caching, polling mode, and comprehensive error handling.

## Features

- **Dual Operation Modes**: On-demand and polling modes
- **Intelligent Caching**: LRU cache with 10-minute TTL and 10-city limit
- **Error Handling**: Comprehensive exception hierarchy
- **Singleton Pattern**: Single instance per API key
- **Thread-Safe**: Designed for concurrent use
- **JSON Response**: Standardized JSON output format

## Installation

### Maven
```xml
