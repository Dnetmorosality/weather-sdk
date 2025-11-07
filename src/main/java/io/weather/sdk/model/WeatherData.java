package io.weather.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * @author rus.sadykov
 * 04.11.2025
 */
@Getter
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
            Weather weather,
            Temperature temperature,
            Integer visibility,
            Wind wind,
            Long datetime,
            Sys sys,
            Integer timezone,
            String name) {
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

    private static final long CACHE_TTL_MS = 10L * 60 * 1000; // 10 minutes

    @JsonProperty("weather")
    public Weather getWeather() {
        return weather;
    }

    @JsonProperty("temperature")
    public Temperature getTemperature() {
        return temperature;
    }

    @JsonProperty("visibility")
    public Integer getVisibility() {
        return visibility;
    }

    @JsonProperty("wind")
    public Wind getWind() {
        return wind;
    }

    @JsonProperty("datetime")
    public Long getDatetime() {
        return datetime;
    }

    @JsonProperty("sys")
    public Sys getSys() {
        return sys;
    }

    @JsonProperty("timezone")
    public Integer getTimezone() {
        return timezone;
    }

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    @JsonIgnore
    public Long getLastUpdated() {
        return lastUpdated;
    }

    @JsonIgnore
    public boolean isDataFresh() {
        return (System.currentTimeMillis() - lastUpdated) < (CACHE_TTL_MS);
    }

    @JsonIgnore
    public boolean isDataStale() {
        return !isDataFresh();
    }

    @Override
    public String toString() {
        return String.format("WeatherData[name=%s, temp=%.1f, weather=%s]",
                name, temperature.temp(), weather.main());
    }
}
