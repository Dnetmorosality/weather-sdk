package io.weather.sdk.exception;

/**
 * @author rus.sadykov
 * 05.11.2025
 */
public class InvalidApiKeyException extends WeatherSDKException {
    public InvalidApiKeyException(String message) {
        super(message);
    }
}
