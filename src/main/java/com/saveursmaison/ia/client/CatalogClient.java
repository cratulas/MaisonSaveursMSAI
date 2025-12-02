package com.saveursmaison.ia.client;

import com.saveursmaison.ia.dto.WineForAiDto;
import com.saveursmaison.ia.dto.CheeseForAiDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
public class CatalogClient {

    private final WebClient catalogWebClient;

    public CatalogClient(@Qualifier("catalogWebClient") WebClient catalogWebClient) {
        this.catalogWebClient = catalogWebClient;
    }

    public List<WineForAiDto> getWinesForAi() {
        return catalogWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/catalog/ai/wines")
                        .queryParam("inStock", true)
                        .build())
                .retrieve()
                .bodyToMono(WineForAiDto[].class)
                .onErrorResume(ex -> {
                    ex.printStackTrace();
                    return Mono.just(new WineForAiDto[0]);
                })
                .map(Arrays::asList)
                .blockOptional()
                .orElse(Collections.emptyList());
    }

    public List<CheeseForAiDto> getCheesesForAi() {
        return catalogWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/catalog/ai/cheeses")
                        .queryParam("inStock", true)
                        .build())
                .retrieve()
                .bodyToMono(CheeseForAiDto[].class)
                .onErrorResume(ex -> {
                    ex.printStackTrace();
                    return Mono.just(new CheeseForAiDto[0]);
                })
                .map(Arrays::asList)
                .blockOptional()
                .orElse(Collections.emptyList());
    }
}
