package com.qaima.service;

import com.qaima.common.Blocking;
import com.qaima.domain.AuthLoginLog;
import com.qaima.repository.AuthLoginLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AuthLoginLogService {

    private final AuthLoginLogRepository authLoginLogRepository;

    public Mono<Void> event(String eventType,
                            boolean success,
                            Long userId,
                            String email,
                            String ip,
                            String userAgent,
                            String errorCode,
                            String message) {
        return Blocking.run(() -> authLoginLogRepository.save(
                AuthLoginLog.event(eventType, success, userId, email, ip, userAgent, errorCode, message)
        ));
    }

    public Mono<Void> success(Long userId, String email, String ip, String userAgent) {
        return Blocking.run(() -> authLoginLogRepository.save(
                AuthLoginLog.success(userId, email, ip, userAgent)
        ));
    }

    public Mono<Void> failure(String email, String ip, String userAgent, String errorCode, String message) {
        return Blocking.run(() -> authLoginLogRepository.save(
                AuthLoginLog.failure(email, ip, userAgent, errorCode, message)
        ));
    }
}
