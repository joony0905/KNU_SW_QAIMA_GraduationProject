package com.qaima.service.resolver;

import com.qaima.domain.Exchange;
import com.qaima.domain.Sector;
import com.qaima.repository.SectorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class SectorResolver {

    public static final String SCHEME_KRX_BZTP_M = "KRX_BZTP_M";

    private final SectorRepository sectorRepository;

    public Mono<Sector> resolve(Exchange exchange, String code, String name) {
        if (code == null || code.isBlank()) return Mono.empty(); // 추론 금지: 코드 없으면 저장 안 함
        final String scheme = SCHEME_KRX_BZTP_M;

        return Mono.fromCallable(() -> sectorRepository.findByExchangeExchangeIdAndSchemeAndCode(
                        exchange.getExchangeId(),
                        scheme,
                        code
                ))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(opt -> opt.map(Mono::just).orElseGet(() -> insertRecover(exchange, scheme, code, name)));
    }

    private Mono<Sector> insertRecover(Exchange exchange, String scheme, String code, String name) {
        return Mono.fromCallable(() -> {
                    Sector s = new Sector();
                    s.setExchange(exchange);
                    s.setScheme(scheme);
                    s.setCode(code);
                    s.setName(name != null ? name : code);
                    return sectorRepository.save(s);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(DataIntegrityViolationException.class, e -> {
                    log.warn("[SectorResolver] unique conflict -> re-fetch. exchange={}, scheme={}, code={}",
                            exchange.getCode(), scheme, code);
                    return Mono.fromCallable(() -> sectorRepository.findByExchangeExchangeIdAndSchemeAndCode(
                                            exchange.getExchangeId(),
                                            scheme,
                                            code
                                    )
                                    .orElseThrow(() -> e))
                            .subscribeOn(Schedulers.boundedElastic());
                });
    }
}
