package io.weather.sdk.exception;

/**
 * @author rus.sadykov
 * 05.11.2025
 */
public class WeatherSDKException extends Exception {
    public WeatherSDKException(String message) {
        super(message);
    }

    public WeatherSDKException(String message, Throwable cause) {
        super(message, cause);
    }
}
