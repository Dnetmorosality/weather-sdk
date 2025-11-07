package io.weather.sdk.exception;

/**
 * @author rus.sadykov
 * 07.11.2025
 */
public class ApiUnavailableException extends WeatherSDKException {
    public ApiUnavailableException(String message) {
        super(message);
    }
}
