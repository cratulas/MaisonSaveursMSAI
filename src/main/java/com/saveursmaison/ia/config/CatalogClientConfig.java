package com.saveursmaison.ia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class CatalogClientConfig {

    @Bean(name = "catalogWebClient")
    public WebClient catalogWebClient(
            @Value("${catalog.base-url}") String baseUrl,
            WebClient.Builder builder
    ) {
        return builder
                .baseUrl(baseUrl)
                .build();
    }
}
