package io.weather.sdk.exception;

/**
 * @author rus.sadykov
 * 05.11.2025
 */
public class RateLimitExceededException extends WeatherSDKException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
