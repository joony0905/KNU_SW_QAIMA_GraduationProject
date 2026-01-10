package com.qaima.common;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Callable;

public final class Blocking {
    private Blocking() {}

    public static <T> Mono<T> call(Callable<T> c) {
        return Mono.fromCallable(c)
                .subscribeOn(Schedulers.boundedElastic());
    }

    public static Mono<Void> run(Runnable r) {
        return Mono.fromRunnable(r)
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}
