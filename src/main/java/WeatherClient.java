import io.weather.sdk.exception.WeatherSDKException;

import java.util.Map;

public interface WeatherClient {
    String getWeather(String cityName) throws WeatherSDKException;
    Map<String, Object> getCacheStats();
    void evictFromCache(String cityName);
    void clearCache();
}
