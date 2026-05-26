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
import java.util.HashMap;
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

        return Blocking.call(() -> {
            DictionaryTerm term = resolveCanonicalTerm(rawTerm, normalizedTerm);
            return toDto(term, preferredEnglishTerm(List.of(term)).get(term.getTerm()));
        });
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
                LikePatterns likePatterns = LikePatterns.from(normalizedQuery);
                list = dictionaryRepository.searchByQueryIncludingAliases(
                        normalizedQuery,
                        likePatterns.prefix(),
                        likePatterns.contains(),
                        initialKey,
                        pageable
                );
            } else if (initialKey != null) {
                list = dictionaryRepository.findByInitialIncludingAliasesOrderByTermAsc(initialKey, pageable);
            } else {
                list = dictionaryRepository.findAllByOrderByTermAsc(pageable);
            }
            return toDtos(list);
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
            LikePatterns likePatterns = LikePatterns.from(prefix);

            dictionaryRepository.findAutocompleteTermCandidates(
                            prefix,
                            likePatterns.prefix(),
                            likePatterns.contains(),
                            candidatePageable
                    )
                    .forEach(term -> addAutocompleteCandidate(
                            suggestions,
                            term.getTerm(),
                            autocompleteScore(term.getTerm(), prefix, false)
                    ));

            dictionaryAliasRepository.findAutocompleteAliasCandidates(
                            prefix,
                            likePatterns.prefix(),
                            likePatterns.contains(),
                            candidatePageable
                    )
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
        String descriptionEn = normalizeOptional(dto.getDescriptionEn());
        String source = normalizeOptional(dto.getSource());
        String sourceOrg = normalizeOptional(dto.getSourceOrg());
        String sourceUrl = normalizeOptional(dto.getSourceUrl());
        String sourceType = normalizeOptional(dto.getSourceType());
        String status = normalizeStatus(dto.getStatus());
        Instant reviewedAt = parseInstant(dto.getReviewedAt(), "reviewedAt");
        String tag = normalizeOptional(dto.getTag());

        return Blocking.call(() -> {
            if (dictionaryAliasRepository.existsByNormalizedAliasTerm(term)) {
                throw new IllegalArgumentException("term must not duplicate an existing alias");
            }

            DictionaryTerm entity = dictionaryRepository.findById(term).orElseGet(() -> new DictionaryTerm(term));
            entity.setTerm(term);
            entity.setDescription(description);
            entity.setDescriptionEn(descriptionEn);
            entity.setSource(source);
            entity.setSourceOrg(sourceOrg);
            entity.setSourceUrl(sourceUrl);
            entity.setSourceType(sourceType);
            entity.setStatus(status);
            entity.setReviewedAt(reviewedAt);
            entity.setTag(tag);
            DictionaryTerm saved = dictionaryRepository.save(entity);
            return toDto(saved, preferredEnglishTerm(List.of(saved)).get(saved.getTerm()));
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

    private List<DictionaryTermDto> toDtos(List<DictionaryTerm> entities) {
        Map<String, String> termEnByTerm = preferredEnglishTerm(entities);
        return entities.stream()
                .map(entity -> toDto(entity, termEnByTerm.get(entity.getTerm())))
                .toList();
    }

    private Map<String, String> preferredEnglishTerm(List<DictionaryTerm> entities) {
        List<String> terms = entities.stream()
                .map(DictionaryTerm::getTerm)
                .toList();
        Map<String, String> result = new HashMap<>();
        if (terms.isEmpty()) return result;

        dictionaryAliasRepository.findByCanonicalTerm_TermInOrderByAliasTermAsc(terms)
                .stream()
                .filter(alias -> isEnglishDisplayAlias(alias.getAliasTerm()))
                .sorted(DictionaryService::compareEnglishAliases)
                .forEach(alias -> {
                    String canonicalTerm = alias.getCanonicalTerm() == null
                            ? null
                            : alias.getCanonicalTerm().getTerm();
                    if (canonicalTerm != null) {
                        result.putIfAbsent(canonicalTerm, alias.getAliasTerm().trim());
                    }
                });
        return result;
    }

    private static DictionaryTermDto toDto(DictionaryTerm entity, String termEn) {
        if (entity == null) return null;
        return DictionaryTermDto.builder()
                .term(entity.getTerm())
                .termEn(resolveTermEn(entity, termEn))
                .initial(entity.getInitial())
                .description(entity.getDescription())
                .descriptionEn(entity.getDescriptionEn())
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

    private static String resolveTermEn(DictionaryTerm entity, String aliasTermEn) {
        if (aliasTermEn != null && !aliasTermEn.isBlank()) {
            return aliasTermEn.trim();
        }
        String term = entity.getTerm();
        return isEnglishDisplayAlias(term) ? term : null;
    }

    private static boolean isEnglishDisplayAlias(String value) {
        if (value == null) return false;
        String trimmed = value.trim();
        return !trimmed.isBlank()
                && trimmed.matches(".*[A-Za-z].*")
                && !trimmed.matches(".*[가-힣].*")
                && !trimmed.contains("_");
    }

    private static int compareEnglishAliases(DictionaryAlias left, DictionaryAlias right) {
        int scoreCompare = Integer.compare(englishAliasScore(left), englishAliasScore(right));
        if (scoreCompare != 0) return scoreCompare;
        return Integer.compare(right.getAliasTerm().length(), left.getAliasTerm().length());
    }

    private static int englishAliasScore(DictionaryAlias alias) {
        String notes = alias.getNotes() == null ? "" : alias.getNotes();
        String sourceType = alias.getSourceType() == null ? "" : alias.getSourceType();
        if (notes.contains("영문") || "planned_alias".equalsIgnoreCase(sourceType)) return 0;
        return 1;
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

    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
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

    private record LikePatterns(String prefix, String contains) {

        private static LikePatterns from(String normalizedQuery) {
            String escaped = escapeLike(normalizedQuery);
            return new LikePatterns(escaped + "%", "%" + escaped + "%");
        }
    }
}
