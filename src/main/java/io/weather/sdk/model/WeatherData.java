package io.weather.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author rus.sadykov
 * 04.11.2025
 */
public class WeatherData {
    @JsonProperty("weather")
    private final Weather weather;

    @JsonProperty("temperature")
    private final Temperature temperature;

    @JsonProperty("visibility")
    private final Integer visibility;

    @JsonProperty("wind")
    private final Wind wind;

    @JsonProperty("datetime")
    private final Long datetime;

    @JsonProperty("sys")
    private final Sys sys;

    @JsonProperty("timezone")
    private final Integer timezone;

    @JsonProperty("name")
    private final String name;

    private final Long lastUpdated;

    public WeatherData(
            @JsonProperty("weather") Weather weather,
            @JsonProperty("temperature") Temperature temperature,
            @JsonProperty("visibility") Integer visibility,
            @JsonProperty("wind") Wind wind,
            @JsonProperty("datetime") Long datetime,
            @JsonProperty("sys") Sys sys,
            @JsonProperty("timezone") Integer timezone,
            @JsonProperty("name") String name) {

        this.weather = weather;
        this.temperature = temperature;
        this.visibility = visibility;
        this.wind = wind;
        this.datetime = datetime;
        this.sys = sys;
        this.timezone = timezone;
        this.name = name;
        this.lastUpdated = System.currentTimeMillis();
    }

    public Weather weather() {
        return weather;
    }

    public Temperature temperature() {
        return temperature;
    }

    public Integer visibility() {
        return visibility;
    }

    public Wind wind() {
        return wind;
    }

    public Long datetime() {
        return datetime;
    }

    public Sys sys() {
        return sys;
    }

    public Integer timezone() {
        return timezone;
    }

    public String name() {
        return name;
    }

    public Long lastUpdated() {
        return lastUpdated;
    }

    public String toJson() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(this);
    }

    public boolean isDataFresh() {
        return lastUpdated != null &&
                (System.currentTimeMillis() - lastUpdated) < 10 * 60 * 1000; // 10 minutes
    }

    @Override
    public String toString() {
        return String.format("WeatherData[name=%s, temp=%.1f, weather=%s]",
                name, temperature.temp(), weather.main());
    }
}
