package com.qaima.service;

import com.qaima.common.Blocking;
import com.qaima.domain.ApiRequestLog;
import com.qaima.repository.ApiRequestLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ApiRequestLogService {

    private final ApiRequestLogRepository apiRequestLogRepository;

    public Mono<Void> save(ApiRequestLog log) {
        return Blocking.run(() -> apiRequestLogRepository.save(log));
    }
}
