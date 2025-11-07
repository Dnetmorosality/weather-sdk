package io.weather.sdk.exception;

/**
 * @author rus.sadykov
 * 05.11.2025
 */
public class CityNotFoundException extends WeatherSDKException {
    public CityNotFoundException(String message) {
        super(message);
    }
}
