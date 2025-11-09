package io.weather.sdk;

import io.weather.sdk.exception.WeatherSDKException;
import io.weather.sdk.model.WeatherData;

@FunctionalInterface
public interface WeatherApiCall {
    WeatherData execute() throws WeatherSDKException;
}
