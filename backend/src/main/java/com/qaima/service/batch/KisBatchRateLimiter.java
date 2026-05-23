package com.qaima.service.batch;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@RequiredArgsConstructor
public class KisBatchRateLimiter {

    private static final int KIS_HARD_LIMIT_PER_SECOND = 15;

    private final KisBatchProperties properties;
    private final Object lock = new Object();

    private Instant windowStart = Instant.now();
    private int usedInWindow = 0;

    public Mono<Void> acquireMono() {
        return Mono.fromRunnable(this::acquire)
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    public void acquire() {
        synchronized (lock) {
            int maxPerSecond = Math.max(
                    1,
                    Math.min(properties.getRateLimit().getMaxRequestsPerSecond(), KIS_HARD_LIMIT_PER_SECOND)
            );
            Instant now = Instant.now();
            long elapsedMs = Duration.between(windowStart, now).toMillis();

            if (elapsedMs >= 1000) {
                windowStart = now;
                usedInWindow = 0;
                elapsedMs = 0;
            }

            if (usedInWindow >= maxPerSecond) {
                sleep(1000 - elapsedMs);
                windowStart = Instant.now();
                usedInWindow = 0;
            }

            usedInWindow++;
        }
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for KIS rate limit", e);
        }
    }
}
