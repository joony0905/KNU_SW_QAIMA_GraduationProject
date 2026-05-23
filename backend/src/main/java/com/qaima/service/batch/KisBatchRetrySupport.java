package com.qaima.service.batch;

import com.qaima.common.ErrorCode;
import com.qaima.common.ErrorException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisBatchRetrySupport {

    public <T> T call(String target, KisBatchProperties.Retry retry, Callable<T> callable) throws Exception {
        return callWithOutcome(target, retry, callable).value();
    }

    public <T> RetryOutcome<T> callWithOutcome(
            String target,
            KisBatchProperties.Retry retry,
            Callable<T> callable
    ) throws Exception {
        int maxAttempts = retry == null ? 1 : Math.max(1, retry.getMaxAttempts());
        long delayMs = retry == null ? 0 : Math.max(0, retry.getDelayMs());
        Exception last = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return new RetryOutcome<>(callable.call(), attempt);
            } catch (Exception e) {
                last = e;
                if (!isRetryable(e) || attempt >= maxAttempts) {
                    if (attempt > 1) {
                        throw new RetryFailedException(e, attempt);
                    }
                    throw e;
                }
                log.warn("[KIS Batch] retryable failure. target={}, attempt={}/{}, delayMs={}, cause={}",
                        target, attempt, maxAttempts, delayMs, e.getMessage());
                sleep(delayMs);
            }
        }

        throw last == null ? new IllegalStateException("retry failed without captured exception") : last;
    }

    public boolean isTimeLimited(Throwable e) {
        return e instanceof ErrorException errorException
                && ErrorCode.KIS_MARKET_CLOSED.equals(errorException.getErrorCode());
    }

    private boolean isRetryable(Throwable e) {
        if (e instanceof ErrorException errorException) {
            return ErrorCode.KIS_HTTP_ERROR.equals(errorException.getErrorCode());
        }
        if (e instanceof WebClientRequestException) {
            return true;
        }
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof ConnectException
                    || cause instanceof SocketException
                    || cause instanceof SocketTimeoutException
                    || cause instanceof TimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private void sleep(long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during KIS retry delay", e);
        }
    }

    public record RetryOutcome<T>(T value, int attempts) {
        public boolean recoveredByRetry() {
            return attempts > 1;
        }
    }

    public static class RetryFailedException extends Exception {
        private final int attempts;

        public RetryFailedException(Exception cause, int attempts) {
            super(cause.getMessage(), cause);
            this.attempts = attempts;
        }

        public int attempts() {
            return attempts;
        }
    }
}
