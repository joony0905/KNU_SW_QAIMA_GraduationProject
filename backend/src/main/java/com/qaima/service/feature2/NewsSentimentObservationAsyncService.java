package com.qaima.service.feature2;

import com.qaima.domain.News;
import com.qaima.domain.NewsSentimentObservation;
import com.qaima.repository.NewsRepository;
import com.qaima.repository.NewsSentimentObservationRepository;
import com.qaima.service.feature2.model.NewsSentimentObservationCommand;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsSentimentObservationAsyncService {

    private final NewsRepository newsRepository;
    private final NewsSentimentObservationRepository observationRepository;

    @Async
    @Transactional
    public void saveObservation(NewsSentimentObservationCommand command) {
        if (command == null
                || command.newsId() == null
                || command.stockCode() == null
                || command.stockCode().isBlank()
                || command.focusText() == null
                || command.focusText().isBlank()
                || command.predictedScore() == null
                || command.modelVersion() == null
                || command.promptVersion() == null
                || command.focusTextVersion() == null
                || command.focusTextVersion().isBlank()) {
            return;
        }

        News news = newsRepository.findById(command.newsId()).orElse(null);
        if (news == null) {
            return;
        }

        if (isDuplicateNewsObservation(command)) {
            log.debug("[NewsSentimentObservationAsyncService] duplicate news observation ignored. stockCode={}, title={}, publishedAt={}",
                    command.stockCode(), command.title(), command.publishedAt());
            return;
        }

        try {
            NewsSentimentObservation observation = new NewsSentimentObservation();
            observation.setNews(news);
            observation.setStockCode(command.stockCode());
            observation.setTitle(command.title());
            observation.setUrl(command.url());
            observation.setPublisher(command.publisher());
            observation.setPublishedAt(command.publishedAt());
            observation.setFocusText(command.focusText());
            observation.setPredictedScore(command.predictedScore());
            observation.setPredictedLabel(command.predictedLabel());
            observation.setNegativeProb(command.negativeProb());
            observation.setNeutralProb(command.neutralProb());
            observation.setPositiveProb(command.positiveProb());
            observation.setModelVersion(command.modelVersion());
            observation.setPromptVersion(command.promptVersion());
            observation.setInputFormatVersion(command.inputFormatVersion());
            observation.setFocusTextVersion(command.focusTextVersion());
            observation.setCreatedAt(OffsetDateTime.now());
            observationRepository.save(observation);
        } catch (DataIntegrityViolationException ex) {
            log.debug("[NewsSentimentObservationAsyncService] duplicate observation ignored. newsId={}, stockCode={}",
                    command.newsId(), command.stockCode());
        } catch (Exception ex) {
            log.warn("[NewsSentimentObservationAsyncService] failed to save observation. newsId={}, stockCode={}",
                    command.newsId(), command.stockCode(), ex);
        }
    }

    private boolean isDuplicateNewsObservation(NewsSentimentObservationCommand command) {
        if (command.title() == null || command.title().isBlank() || command.inputFormatVersion() == null) {
            return false;
        }

        if (command.publishedAt() != null) {
            return observationRepository.existsByStockCodeAndTitleAndPublishedAtAndModelVersionAndInputFormatVersion(
                    command.stockCode(),
                    command.title(),
                    command.publishedAt(),
                    command.modelVersion(),
                    command.inputFormatVersion()
            );
        }

        return observationRepository.existsByStockCodeAndTitleAndModelVersionAndInputFormatVersion(
                command.stockCode(),
                command.title(),
                command.modelVersion(),
                command.inputFormatVersion()
        );
    }
}
