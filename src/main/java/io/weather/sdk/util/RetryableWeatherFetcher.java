package io.weather.sdk.util;

import io.weather.sdk.WeatherApiCall;
import io.weather.sdk.config.RetryConfig;
import io.weather.sdk.exception.RateLimitExceededException;
import io.weather.sdk.exception.WeatherSDKException;
import io.weather.sdk.model.WeatherData;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RetryableWeatherFetcher {
    private final RetryConfig retryConfig;

    public WeatherData fetchWithRetry(String cityName, WeatherApiCall apiCall)
            throws WeatherSDKException {

        int retryCount = 0;
        Exception lastException = null;

        while (retryCount <= retryConfig.getMaxRetries()) {
            try {
                return apiCall.execute();
            } catch (RateLimitExceededException e) {
                lastException = e;

                if (retryCount == retryConfig.getMaxRetries()) {
                    break;
                }

                try {
                    exponentialBackoff(retryCount, retryConfig);
                    retryCount++;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new WeatherSDKException("Retry interrupted", ie);
                }
            } catch (Exception e) {
                throw new WeatherSDKException("Retry canceled", e);
            }
        }

        throw new WeatherSDKException(
                String.format("Failed after %d retries for city: %s",
                        retryConfig.getMaxRetries(), cityName),
                lastException
        );
    }

    private void exponentialBackoff(int retryCount, RetryConfig config)
            throws InterruptedException {

        long delayMs = Math.min(
                config.getBaseDelayMs() * (1L << retryCount),
                config.getMaxDelayMs()
        );

        double jitter = config.getJitterFactor() * delayMs * (2 * Math.random() - 1);
        long finalDelayMs = Math.max(100, (long) (delayMs + jitter));

        Thread.sleep(finalDelayMs);
    }
}
