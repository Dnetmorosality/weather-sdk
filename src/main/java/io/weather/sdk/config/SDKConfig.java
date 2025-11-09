package io.weather.sdk.config;

import lombok.Builder;
import lombok.Value;

import java.time.Duration;

@Value
@Builder
public class SDKConfig {
    @Builder.Default
    int cacheSize = 10;

    @Builder.Default
    long pollingIntervalMs = 300_000; // 5 minutes

    @Builder.Default
    long cacheTtlMs = 600_000; // 10 minutes

    @Builder.Default
    Duration httpTimeout = Duration.ofSeconds(30);

    @Builder.Default
    RetryConfig retryConfig = RetryConfig.defaultConfig();

    public static SDKConfig defaultConfig() {
        return SDKConfig.builder().build();
    }

    public static SDKConfig onDemandConfig() {
        return SDKConfig.builder()
                .cacheSize(10)
                .cacheTtlMs(600_000) // 10 minutes for on-demand
                .build();
    }

    public static SDKConfig pollingConfig() {
        return SDKConfig.builder()
                .cacheSize(10)
                .pollingIntervalMs(300_000) // 5 minutes for polling
                .cacheTtlMs(600_000) // 10 minutes
                .build();
    }
}
