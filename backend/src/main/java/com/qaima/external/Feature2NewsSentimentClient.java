package com.qaima.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaima.external.dto.feature2news.Feature2NewsSentimentExternalEnvelopeDto;
import com.qaima.external.dto.feature2news.Feature2NewsSentimentExternalItemDto;
import com.qaima.external.dto.feature2news.Feature2NewsSentimentExternalRequestDto;
import com.qaima.external.dto.feature2news.Feature2NewsSentimentExternalResponseDto;
import com.qaima.service.feature2.model.NewsSentimentInput;
import com.qaima.service.feature2.model.NewsSentimentResult;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
public class Feature2NewsSentimentClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public Feature2NewsSentimentClient(
            @Qualifier("analysisWebClient") WebClient webClient,
            ObjectMapper objectMapper
    ) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    public SentimentBatchResponse analyze(List<NewsSentimentInput> items, String model) {
        // TODO: 현재는 Spring이 정한 모델명을 전달하고, 추후 클라이언트 선택 모델을 그대로 넘기도록 확장한다.
        Feature2NewsSentimentExternalRequestDto requestDto = Feature2NewsSentimentExternalRequestDto.builder()
                .items(items.stream()
                        .map(item -> Feature2NewsSentimentExternalItemDto.builder()
                                .url(item.url())
                                .title(item.title())
                                .publisher(item.publisher())
                                .publishedAt(item.publishedAt())
                                .focusText(item.focusText())
                                .build())
                        .toList())
                .model(model)
                .build();

        String body = webClient.post()
                .uri("/feature2/news-sentiment")
                .bodyValue(requestDto)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            Feature2NewsSentimentExternalEnvelopeDto envelope = tryReadEnvelope(body);
            Feature2NewsSentimentExternalResponseDto payload = envelope != null && envelope.getData() != null
                    ? envelope.getData()
                    : objectMapper.readValue(body, Feature2NewsSentimentExternalResponseDto.class);

            if (payload == null) {
                throw new IllegalStateException("FastAPI feature2/news-sentiment payload is missing");
            }

            List<String> warnings = new ArrayList<>();
            Set<String> invalidScoreUrls = new LinkedHashSet<>();
            if (envelope != null && envelope.getMeta() != null && envelope.getMeta().getWarnings() != null) {
                warnings.addAll(envelope.getMeta().getWarnings());
            }
            if (payload.getWarnings() != null) {
                warnings.addAll(payload.getWarnings());
            }

            if (payload.getResults() == null) {
                warnings.add("NEWS_SENTIMENT_INVALID_RESPONSE");
                return new SentimentBatchResponse(List.of(), dedupe(warnings), List.of());
            }

            List<NewsSentimentResult> results = new ArrayList<>();
            payload.getResults().forEach(item -> {
                if (item == null || item.getUrl() == null || item.getUrl().isBlank()) {
                    warnings.add("NEWS_SENTIMENT_INVALID_RESPONSE");
                    return;
                }
                if (item.getSentimentScore() == null) {
                    warnings.add("NEWS_SENTIMENT_INVALID_RESPONSE");
                    invalidScoreUrls.add(item.getUrl());
                    return;
                }
                results.add(new NewsSentimentResult(item.getUrl(), item.getSentimentScore()));
            });
            return new SentimentBatchResponse(results, dedupe(warnings), List.copyOf(invalidScoreUrls));
        } catch (Exception ex) {
            throw new RuntimeException("FEATURE2_NEWS_SENTIMENT_DECODE_FAILED", ex);
        }
    }

    private Feature2NewsSentimentExternalEnvelopeDto tryReadEnvelope(String body) {
        try {
            return objectMapper.readValue(body, Feature2NewsSentimentExternalEnvelopeDto.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<String> dedupe(List<String> warnings) {
        List<String> deduped = new ArrayList<>();
        for (String warning : warnings) {
            if (warning != null && !warning.isBlank() && !deduped.contains(warning)) {
                deduped.add(warning);
            }
        }
        return deduped;
    }

    public record SentimentBatchResponse(
            List<NewsSentimentResult> results,
            List<String> warnings,
            List<String> invalidScoreUrls
    ) {
    }
}
