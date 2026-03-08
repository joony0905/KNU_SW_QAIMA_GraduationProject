package com.qaima.service;
import com.qaima.external.TestExternalClient;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ExternalTestService {

    private final TestExternalClient client;

    public Mono<String> getPost(int id) {
        return client.getPost(id);
    }
}
