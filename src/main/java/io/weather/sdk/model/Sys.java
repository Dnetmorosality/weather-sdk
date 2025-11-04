package io.weather.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author rus.sadykov
 * 04.11.2025
 */
public record Sys(@JsonProperty("sunrise") Long sunrise,
                  @JsonProperty("sunset") Long sunset) {
}
