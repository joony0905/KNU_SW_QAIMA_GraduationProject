package com.qaima.service.resolver;

import com.qaima.domain.Exchange;
import com.qaima.domain.Industry;
import com.qaima.domain.Sector;
import com.qaima.repository.IndustryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class IndustryResolver {

    public static final String SCHEME_KRX_BZTP_S = "KRX_BZTP_S";

    private final IndustryRepository industryRepository;

    public Mono<Industry> resolve(Exchange exchange, String code, String name, Sector sector) {
        if (code == null || code.isBlank() || sector == null) return Mono.empty();
        final String scheme = SCHEME_KRX_BZTP_S;

        return Mono.fromCallable(() -> industryRepository.findByExchangeExchangeIdAndSectorSectorIdAndSchemeAndCode(
                        exchange.getExchangeId(),
                        sector.getSectorId(),
                        scheme,
                        code
                ))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(opt -> opt.map(Mono::just).orElseGet(() -> insertRecover(exchange, scheme, code, name, sector)));
    }

    private Mono<Industry> insertRecover(Exchange exchange, String scheme, String code, String name, Sector sector) {
        return Mono.fromCallable(() -> {
                    Industry i = new Industry();
                    i.setExchange(exchange);
                    i.setScheme(scheme);
                    i.setCode(code);
                    i.setName(name != null ? name : code);
                    i.setSector(sector);
                    return industryRepository.save(i);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(DataIntegrityViolationException.class, e -> {
                    log.warn("[IndustryResolver] unique conflict -> re-fetch. exchange={}, sector={}, scheme={}, code={}",
                            exchange.getCode(), sector.getCode(), scheme, code);
                    return Mono.fromCallable(() -> industryRepository.findByExchangeExchangeIdAndSectorSectorIdAndSchemeAndCode(
                                            exchange.getExchangeId(),
                                            sector.getSectorId(),
                                            scheme,
                                            code
                                    )
                                    .orElseThrow(() -> e))
                            .subscribeOn(Schedulers.boundedElastic());
                });
    }
}
