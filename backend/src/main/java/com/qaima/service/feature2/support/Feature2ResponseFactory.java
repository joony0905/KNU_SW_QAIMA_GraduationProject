package com.qaima.service.feature2.support;

import com.qaima.dto.feature2.Feature2AnalyzeResponseDto;
import com.qaima.dto.feature2.Feature2MetaDto;
import com.qaima.dto.feature2.Feature2MetricsDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class Feature2ResponseFactory {

    public Feature2AnalyzeResponseDto success(Feature2MetricsDto metrics, Feature2MetaDto meta) {
        return success(metrics, meta, null);
    }

    public Feature2AnalyzeResponseDto success(Feature2MetricsDto metrics, Feature2MetaDto meta, String explain) {
        List<String> dedupedWarnings = meta.getWarnings() == null
                ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(meta.getWarnings()));

        Feature2MetaDto normalizedMeta = Feature2MetaDto.builder()
                .warnings(dedupedWarnings)
                .build();

        return Feature2AnalyzeResponseDto.builder()
                .metrics(metrics)
                .explain(explain)
                .warnings(normalizedMeta.getWarnings())
                .build();
    }
}
