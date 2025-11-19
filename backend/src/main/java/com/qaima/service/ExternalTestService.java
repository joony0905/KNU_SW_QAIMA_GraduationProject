package com.qaima.service;
import com.qaima.external.TestExternalClient;
import org.springframework.stereotype.Service;

@Service
public class ExternalTestService {

    private final TestExternalClient client;

    public ExternalTestService(TestExternalClient client) {
        this.client = client;
    }

    public String fetchDummy(int id) {
        return client.getPost(id);
    }
}