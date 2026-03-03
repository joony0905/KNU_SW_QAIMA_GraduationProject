package com.qaima.service.dictionary;

import com.qaima.common.Blocking;
import com.qaima.common.DictionaryTermNormalizer;
import com.qaima.common.exception.ResourceNotFoundException;
import com.qaima.domain.DictionaryTerm;
import com.qaima.dto.dictionary.DictionaryInitialCountDto;
import com.qaima.dto.dictionary.DictionaryTermDto;
import com.qaima.dto.dictionary.DictionaryUpsertRequestDto;
import com.qaima.repository.DictionaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DictionaryService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int DEFAULT_AUTOCOMPLETE_SIZE = 10;
    private static final int MAX_AUTOCOMPLETE_SIZE = 30;

    private final DictionaryRepository dictionaryRepository;

    public Mono<DictionaryTermDto> getByTerm(String rawTerm) {
        String term = DictionaryTermNormalizer.normalizeTerm(rawTerm);
        if (term == null) return Mono.error(new IllegalArgumentException("term is required"));

        return Blocking.call(() -> dictionaryRepository.findById(term)
                        .orElseThrow(() -> new ResourceNotFoundException("Unknown term: " + rawTerm)))
                .map(DictionaryService::toDto);
    }

    public Mono<List<DictionaryTermDto>> search(String q, String initial, Integer page, Integer size) {
        String query = normalizeQuery(q);
        String initialKey = normalizeInitial(initial);

        int resolvedSize = clamp(size, DEFAULT_PAGE_SIZE, 1, MAX_PAGE_SIZE);
        int resolvedPage = Math.max(page == null ? 0 : page, 0);
        Pageable pageable = PageRequest.of(resolvedPage, resolvedSize);

        return Blocking.call(() -> {
            List<DictionaryTerm> list;
            if (initialKey != null && query != null) {
                list = dictionaryRepository.findByInitialAndTermContainingIgnoreCaseOrderByTermAsc(initialKey, query, pageable);
            } else if (initialKey != null) {
                list = dictionaryRepository.findByInitialOrderByTermAsc(initialKey, pageable);
            } else if (query != null) {
                list = dictionaryRepository.findByTermContainingIgnoreCaseOrderByTermAsc(query, pageable);
            } else {
                list = dictionaryRepository.findAllByOrderByTermAsc(pageable);
            }
            return list.stream().map(DictionaryService::toDto).toList();
        });
    }

    public Mono<List<String>> autocomplete(String q, Integer size) {
        String prefix = DictionaryTermNormalizer.normalizeTerm(q);
        if (prefix == null) return Mono.error(new IllegalArgumentException("q is required"));

        int resolvedSize = clamp(size, DEFAULT_AUTOCOMPLETE_SIZE, 1, MAX_AUTOCOMPLETE_SIZE);
        Pageable pageable = PageRequest.of(0, resolvedSize);

        return Blocking.call(() -> dictionaryRepository.findByTermStartingWithIgnoreCaseOrderByTermAsc(prefix, pageable)
                .stream()
                .map(DictionaryTerm::getTerm)
                .toList());
    }

    public Mono<List<DictionaryInitialCountDto>> initialCounts() {
        return Blocking.call(dictionaryRepository::countByInitial);
    }

    public Mono<DictionaryTermDto> upsert(DictionaryUpsertRequestDto dto) {
        if (dto == null) return Mono.error(new IllegalArgumentException("body is required"));
        String term = DictionaryTermNormalizer.normalizeTerm(dto.getTerm());
        if (term == null) return Mono.error(new IllegalArgumentException("term is required"));

        String description = normalizeRequired(dto.getDescription(), "description is required");
        String source = normalizeOptional(dto.getSource());
        String tag = normalizeOptional(dto.getTag());

        return Blocking.call(() -> {
            DictionaryTerm entity = dictionaryRepository.findById(term).orElseGet(() -> new DictionaryTerm(term));
            entity.setTerm(term);
            entity.setDescription(description);
            entity.setSource(source);
            entity.setTag(tag);
            DictionaryTerm saved = dictionaryRepository.save(entity);
            return toDto(saved);
        });
    }

    public Mono<Void> delete(String rawTerm) {
        String term = DictionaryTermNormalizer.normalizeTerm(rawTerm);
        if (term == null) return Mono.error(new IllegalArgumentException("term is required"));

        return Blocking.run(() -> {
            if (!dictionaryRepository.existsById(term)) {
                throw new ResourceNotFoundException("Unknown term: " + rawTerm);
            }
            dictionaryRepository.deleteById(term);
        });
    }

    private static DictionaryTermDto toDto(DictionaryTerm entity) {
        if (entity == null) return null;
        return DictionaryTermDto.builder()
                .term(entity.getTerm())
                .initial(entity.getInitial())
                .description(entity.getDescription())
                .source(entity.getSource())
                .tag(entity.getTag())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private static String normalizeQuery(String q) {
        if (q == null) return null;
        String trimmed = q.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private static String normalizeInitial(String initial) {
        if (initial == null) return null;
        String trimmed = initial.trim();
        if (trimmed.isBlank()) return null;

        String key = DictionaryTermNormalizer.computeInitial(trimmed);
        return "#".equals(key) ? null : key;
    }

    private static String normalizeRequired(String value, String message) {
        if (value == null) throw new IllegalArgumentException(message);
        String trimmed = value.trim();
        if (trimmed.isBlank()) throw new IllegalArgumentException(message);
        return trimmed;
    }

    private static String normalizeOptional(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private static int clamp(Integer value, int defaultValue, int min, int max) {
        if (value == null) return defaultValue;
        if (value < min) return min;
        return Math.min(value, max);
    }
}
