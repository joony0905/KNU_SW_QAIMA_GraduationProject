package com.qaima.service.dictionary;

import com.qaima.common.Blocking;
import com.qaima.common.DictionaryTermNormalizer;
import com.qaima.common.exception.ResourceNotFoundException;
import com.qaima.domain.DictionaryAlias;
import com.qaima.domain.DictionaryTerm;
import com.qaima.dto.dictionary.DictionaryAliasDto;
import com.qaima.dto.dictionary.DictionaryAliasUpsertRequestDto;
import com.qaima.dto.dictionary.DictionaryInitialCountDto;
import com.qaima.dto.dictionary.DictionaryTermDto;
import com.qaima.dto.dictionary.DictionaryUpsertRequestDto;
import com.qaima.repository.DictionaryAliasRepository;
import com.qaima.repository.DictionaryRepository;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class DictionaryService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int DEFAULT_AUTOCOMPLETE_SIZE = 10;
    private static final int MAX_AUTOCOMPLETE_SIZE = 30;
    private static final String DEFAULT_STATUS = "PUBLISHED";

    private final DictionaryRepository dictionaryRepository;
    private final DictionaryAliasRepository dictionaryAliasRepository;

    public Mono<DictionaryTermDto> getByTerm(String rawTerm) {
        String normalizedTerm = DictionaryTermNormalizer.normalizeTerm(rawTerm);
        if (normalizedTerm == null) {
            return Mono.error(new IllegalArgumentException("term is required"));
        }

        return Blocking.call(() -> toDto(resolveCanonicalTerm(rawTerm, normalizedTerm)));
    }

    public Mono<List<DictionaryTermDto>> search(String q, String initial, Integer page, Integer size) {
        String normalizedQuery = normalizeSearchQuery(q);
        String initialKey = normalizeInitial(initial);

        int resolvedSize = clamp(size, DEFAULT_PAGE_SIZE, 1, MAX_PAGE_SIZE);
        int resolvedPage = Math.max(page == null ? 0 : page, 0);
        Pageable pageable = PageRequest.of(resolvedPage, resolvedSize);

        return Blocking.call(() -> {
            List<DictionaryTerm> list;
            if (normalizedQuery != null) {
                list = dictionaryRepository.searchByQueryIncludingAliases(normalizedQuery, initialKey, pageable);
            } else if (initialKey != null) {
                list = dictionaryRepository.findByInitialOrderByTermAsc(initialKey, pageable);
            } else {
                list = dictionaryRepository.findAllByOrderByTermAsc(pageable);
            }
            return list.stream().map(DictionaryService::toDto).toList();
        });
    }

    public Mono<List<String>> autocomplete(String q, Integer size) {
        String prefix = DictionaryTermNormalizer.normalizeTerm(q);
        if (prefix == null) {
            return Mono.error(new IllegalArgumentException("q is required"));
        }

        int resolvedSize = clamp(size, DEFAULT_AUTOCOMPLETE_SIZE, 1, MAX_AUTOCOMPLETE_SIZE);
        Pageable candidatePageable = PageRequest.of(0, Math.min(resolvedSize * 4, MAX_PAGE_SIZE));

        return Blocking.call(() -> {
            Map<String, AutocompleteCandidate> suggestions = new LinkedHashMap<>();

            dictionaryRepository.findAutocompleteTermCandidates(prefix, candidatePageable)
                    .forEach(term -> addAutocompleteCandidate(
                            suggestions,
                            term.getTerm(),
                            autocompleteScore(term.getTerm(), prefix, false)
                    ));

            dictionaryAliasRepository.findAutocompleteAliasCandidates(prefix, candidatePageable)
                    .forEach(alias -> addAutocompleteCandidate(
                            suggestions,
                            alias.getAliasTerm(),
                            autocompleteScore(alias.getAliasTerm(), prefix, true)
                    ));

            return suggestions.values().stream()
                    .sorted(Comparator
                            .comparingInt(AutocompleteCandidate::score)
                            .thenComparing(candidate -> DictionaryTermNormalizer.normalizeTerm(candidate.value()))
                            .thenComparing(AutocompleteCandidate::value))
                    .limit(resolvedSize)
                    .map(AutocompleteCandidate::value)
                    .toList();
        });
    }

    public Mono<List<DictionaryInitialCountDto>> initialCounts() {
        return Blocking.call(dictionaryRepository::countByInitial);
    }

    public Mono<List<DictionaryAliasDto>> getAliases(String rawTerm) {
        String normalizedTerm = DictionaryTermNormalizer.normalizeTerm(rawTerm);
        if (normalizedTerm == null) {
            return Mono.error(new IllegalArgumentException("term is required"));
        }

        return Blocking.call(() -> {
            DictionaryTerm canonical = resolveCanonicalTerm(rawTerm, normalizedTerm);
            return dictionaryAliasRepository.findByCanonicalTerm_TermOrderByAliasTermAsc(canonical.getTerm())
                    .stream()
                    .map(DictionaryService::toAliasDto)
                    .toList();
        });
    }

    public Mono<DictionaryTermDto> upsert(DictionaryUpsertRequestDto dto) {
        if (dto == null) return Mono.error(new IllegalArgumentException("body is required"));

        String term = DictionaryTermNormalizer.normalizeTerm(dto.getTerm());
        if (term == null) return Mono.error(new IllegalArgumentException("term is required"));

        String description = normalizeRequired(dto.getDescription(), "description is required");
        String source = normalizeOptional(dto.getSource());
        String sourceOrg = normalizeOptional(dto.getSourceOrg());
        String sourceUrl = normalizeOptional(dto.getSourceUrl());
        String sourceType = normalizeOptional(dto.getSourceType());
        String status = normalizeStatus(dto.getStatus());
        Instant reviewedAt = parseInstant(dto.getReviewedAt(), "reviewedAt");
        String tag = normalizeOptional(dto.getTag());

        return Blocking.call(() -> {
            DictionaryTerm entity = dictionaryRepository.findById(term).orElseGet(() -> new DictionaryTerm(term));
            entity.setTerm(term);
            entity.setDescription(description);
            entity.setSource(source);
            entity.setSourceOrg(sourceOrg);
            entity.setSourceUrl(sourceUrl);
            entity.setSourceType(sourceType);
            entity.setStatus(status);
            entity.setReviewedAt(reviewedAt);
            entity.setTag(tag);
            DictionaryTerm saved = dictionaryRepository.save(entity);
            return toDto(saved);
        });
    }

    public Mono<DictionaryAliasDto> upsertAlias(DictionaryAliasUpsertRequestDto dto) {
        if (dto == null) return Mono.error(new IllegalArgumentException("body is required"));

        String alias = normalizeRequired(dto.getAlias(), "alias is required");
        String normalizedAlias = DictionaryTermNormalizer.normalizeTerm(alias);
        if (normalizedAlias == null) {
            return Mono.error(new IllegalArgumentException("alias is required"));
        }

        String canonicalTermKey = DictionaryTermNormalizer.normalizeTerm(dto.getCanonicalTerm());
        if (canonicalTermKey == null) {
            return Mono.error(new IllegalArgumentException("canonicalTerm is required"));
        }

        if (normalizedAlias.equals(canonicalTermKey)) {
            return Mono.error(new IllegalArgumentException("alias must differ from canonicalTerm"));
        }

        String sourceType = normalizeOptional(dto.getSourceType());
        String notes = normalizeOptional(dto.getNotes());

        return Blocking.call(() -> {
            if (dictionaryRepository.existsById(normalizedAlias)) {
                throw new IllegalArgumentException("alias must not duplicate an existing term");
            }

            DictionaryTerm canonical = dictionaryRepository.findById(canonicalTermKey)
                    .orElseThrow(() -> new ResourceNotFoundException("Unknown term: " + dto.getCanonicalTerm()));

            DictionaryAlias entity = dictionaryAliasRepository.findByNormalizedAliasTerm(normalizedAlias)
                    .orElseGet(DictionaryAlias::new);
            entity.setCanonicalTerm(canonical);
            entity.setAliasTerm(alias);
            entity.setSourceType(sourceType);
            entity.setNotes(notes);
            DictionaryAlias saved = dictionaryAliasRepository.save(entity);
            return toAliasDto(saved);
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

    public Mono<Void> deleteAlias(String rawAlias) {
        String normalizedAlias = DictionaryTermNormalizer.normalizeTerm(rawAlias);
        if (normalizedAlias == null) {
            return Mono.error(new IllegalArgumentException("alias is required"));
        }

        return Blocking.run(() -> {
            DictionaryAlias alias = dictionaryAliasRepository.findByNormalizedAliasTerm(normalizedAlias)
                    .orElseThrow(() -> new ResourceNotFoundException("Unknown alias: " + rawAlias));
            dictionaryAliasRepository.delete(alias);
        });
    }

    private DictionaryTerm resolveCanonicalTerm(String rawTerm, String normalizedTerm) {
        return dictionaryRepository.findById(normalizedTerm)
                .or(() -> dictionaryAliasRepository.findByNormalizedAliasTerm(normalizedTerm).map(DictionaryAlias::getCanonicalTerm))
                .orElseThrow(() -> new ResourceNotFoundException("Unknown term: " + rawTerm));
    }

    private static DictionaryTermDto toDto(DictionaryTerm entity) {
        if (entity == null) return null;
        return DictionaryTermDto.builder()
                .term(entity.getTerm())
                .initial(entity.getInitial())
                .description(entity.getDescription())
                .source(entity.getSource())
                .sourceOrg(entity.getSourceOrg())
                .sourceUrl(entity.getSourceUrl())
                .sourceType(entity.getSourceType())
                .status(entity.getStatus())
                .reviewedAt(entity.getReviewedAt())
                .tag(entity.getTag())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private static DictionaryAliasDto toAliasDto(DictionaryAlias entity) {
        if (entity == null) return null;
        return DictionaryAliasDto.builder()
                .alias(entity.getAliasTerm())
                .canonicalTerm(entity.getCanonicalTerm() == null ? null : entity.getCanonicalTerm().getTerm())
                .sourceType(entity.getSourceType())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private static String normalizeSearchQuery(String q) {
        return DictionaryTermNormalizer.normalizeTerm(q);
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

    private static String normalizeStatus(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_STATUS;
        }
        return value.trim().toUpperCase();
    }

    private static Instant parseInstant(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(fieldName + " must be ISO-8601 instant");
        }
    }

    private static int clamp(Integer value, int defaultValue, int min, int max) {
        if (value == null) return defaultValue;
        if (value < min) return min;
        return Math.min(value, max);
    }

    private static void addAutocompleteCandidate(
            Map<String, AutocompleteCandidate> suggestions,
            String value,
            int score
    ) {
        String key = DictionaryTermNormalizer.normalizeTerm(value);
        if (key == null) return;

        AutocompleteCandidate next = new AutocompleteCandidate(value, score);
        AutocompleteCandidate current = suggestions.get(key);
        if (current == null
                || next.score() < current.score()
                || (next.score() == current.score() && next.value().compareToIgnoreCase(current.value()) < 0)) {
            suggestions.put(key, next);
        }
    }

    private static int autocompleteScore(String value, String normalizedQuery, boolean alias) {
        String normalizedValue = DictionaryTermNormalizer.normalizeTerm(value);
        if (normalizedValue == null) return 6;
        if (normalizedValue.equals(normalizedQuery)) return alias ? 1 : 0;
        if (normalizedValue.startsWith(normalizedQuery)) return alias ? 3 : 2;
        if (normalizedValue.contains(normalizedQuery)) return alias ? 5 : 4;
        return 6;
    }

    private record AutocompleteCandidate(String value, int score) {
    }
}
