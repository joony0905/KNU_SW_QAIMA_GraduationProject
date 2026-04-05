package com.qaima.dto.featone;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatOneAnalysisExplainOverallDto {
    private String summary;
    private List<String> bullets;
    private List<String> risks;
    private String conclusion;
}
