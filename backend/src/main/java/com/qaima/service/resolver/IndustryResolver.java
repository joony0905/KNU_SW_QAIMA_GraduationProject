package com.qaima.service.resolver;

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

    public Mono<Industry> resolve(String code, String name, Sector sectorOrNull) {
        if (code == null || code.isBlank()) return Mono.empty(); // 추론 금지
        final String scheme = SCHEME_KRX_BZTP_S;

        return Mono.fromCallable(() -> industryRepository.findBySchemeAndCode(scheme, code))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(opt -> opt.map(Mono::just).orElseGet(() -> insertRecover(scheme, code, name, sectorOrNull)));
    }

    private Mono<Industry> insertRecover(String scheme, String code, String name, Sector sectorOrNull) {
        return Mono.fromCallable(() -> {
                    Industry i = new Industry();
                    i.setScheme(scheme);
                    i.setCode(code);
                    i.setName(name != null ? name : code);
                    i.setSector(sectorOrNull);
                    return industryRepository.save(i);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(DataIntegrityViolationException.class, e -> {
                    log.warn("[IndustryResolver] unique conflict -> re-fetch. scheme={}, code={}", scheme, code);
                    return Mono.fromCallable(() -> industryRepository.findBySchemeAndCode(scheme, code)
                                    .orElseThrow(() -> e))
                            .subscribeOn(Schedulers.boundedElastic());
                });
    }
}