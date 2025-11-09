package io.weather.sdk.config;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RetryConfig {
    @Builder.Default
    int maxRetries = 3;

    @Builder.Default
    long baseDelayMs = 1000;

    @Builder.Default
    long maxDelayMs = 30000;

    @Builder.Default
    double jitterFactor = 0.3;

    public static RetryConfig defaultConfig() {
        return RetryConfig.builder().build();
    }
}
