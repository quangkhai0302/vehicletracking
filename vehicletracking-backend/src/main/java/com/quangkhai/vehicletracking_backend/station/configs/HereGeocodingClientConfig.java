package com.quangkhai.vehicletracking_backend.station.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration 
public class HereGeocodingClientConfig {
    
    @Bean(name = "hereGeocodingRestClient")
    public RestClient hereGeocodingRestClient(
        HereGeocodingProperties properties
    ) {
        SimpleClientHttpRequestFactory factory = 
            new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(
            Duration.ofMillis(properties.connectTimeoutMs()));

        factory.setReadTimeout(
            Duration.ofMillis(properties.readTimeoutMs()));

        return RestClient.builder()
            .baseUrl("https://revgeocode.search.hereapi.com")
            .requestFactory(factory)
            .build();
    }

}
