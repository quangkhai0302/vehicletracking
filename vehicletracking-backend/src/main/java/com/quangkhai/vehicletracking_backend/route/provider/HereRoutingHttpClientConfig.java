package com.quangkhai.vehicletracking_backend.route.provider;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class HereRoutingHttpClientConfig {

    @Bean
    public RestClient hereRoutingRestClient(HereRoutingProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }
}
