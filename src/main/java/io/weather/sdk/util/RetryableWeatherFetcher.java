package io.weather.sdk.util;

import io.weather.sdk.WeatherApiCall;
import io.weather.sdk.config.RetryConfig;
import io.weather.sdk.exception.ApiUnavailableException;
import io.weather.sdk.exception.RateLimitExceededException;
import io.weather.sdk.exception.WeatherSDKException;
import io.weather.sdk.model.WeatherData;

public record RetryableWeatherFetcher(RetryConfig retryConfig) {

    /**
     * Executes the provided {@link WeatherApiCall} with a retry strategy.
     * <p>
     * Retries are applied when the call throws a {@link RateLimitExceededException}
     * or an {@link ApiUnavailableException}. The backoff between retries uses
     * exponential delay with jitter, bounded by the limits defined in the
     * associated {@link RetryConfig} of this fetcher.
     * </p>
     *
     * @param cityName the city for which weather data is requested; used for error context
     * @param apiCall  the callable that performs the actual API request
     * @return the successfully retrieved {@link WeatherData}
     * @throws WeatherSDKException if the error is not retryable, there are no attempts left,
     *                              the retry is interrupted, or the retry flow terminates unexpectedly
     */
    public WeatherData fetchWithRetry(String cityName, WeatherApiCall apiCall) throws WeatherSDKException {

        int maxRetries = retryConfig.getMaxRetries();
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return apiCall.execute();
            } catch (WeatherSDKException e) {
                boolean retryable = (e instanceof RateLimitExceededException) || (e instanceof ApiUnavailableException);
                boolean hasAttemptsLeft = attempt < maxRetries;

                if (!retryable || !hasAttemptsLeft) {
                    throw e;
                }

                try {
                    exponentialBackoff(attempt, retryConfig);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new WeatherSDKException("Retry interrupted", ie);
                }
            }
        }
        throw new WeatherSDKException("Unexpected retry flow termination for city: " + cityName);
    }

    /**
     * Sleeps for an exponentially increasing delay with optional jitter.

     * The delay is computed as:
     * {@code baseDelayMs * 2^{retryCount}}, capped at {@code maxDelayMs} from the provided
     * {@link RetryConfig}. A symmetric random jitter in the range
     * {@code [-jitterFactor * delay, +jitterFactor * delay]} is then applied. The final delay is
     * lower-bounded to 100 ms to avoid zero or near-zero sleeps.

     *
     * @param retryCount the current retry attempt (0-based) used to scale the delay
     * @param config     the {@link RetryConfig} providing base, max and jitter parameters
     * @throws InterruptedException if the current thread is interrupted while sleeping
     */
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
