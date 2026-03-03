package com.qaima.external;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class TestExternalClient {

    private final WebClient webClient;

    public TestExternalClient(WebClient.Builder builder) {
        // 임시로 JSONPlaceholder 같은 무료 API 사용
        this.webClient = builder
                .baseUrl("https://jsonplaceholder.typicode.com")
                .build();
    }

    public String getPost(int id) {
        return webClient.get()
                .uri("/posts/{id}", id)
                .retrieve()
                .bodyToMono(String.class)
                .block();  // 테스트니까 block() 해도 됨
    }
}
