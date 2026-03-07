package com.qaima.external;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class TestExternalClient {

    private final WebClient webClient;

    public TestExternalClient(WebClient.Builder builder) {
        this.webClient = builder
                .baseUrl("https://jsonplaceholder.typicode.com")
                .build();
    }

    public Mono<String> getPost(int id) {
        return webClient.get()
                .uri("/posts/{id}", id)
                .retrieve()
                .bodyToMono(String.class);
    }
}
