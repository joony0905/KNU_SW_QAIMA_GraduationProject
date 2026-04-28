package com.qaima.dto.dictionary;

import jakarta.validation.constraints.NotBlank;
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
public class DictionaryUpsertRequestDto {

    @NotBlank(message = "term is required")
    private String term;

    @NotBlank(message = "description is required")
    private String description;

    private String source;

    private String sourceOrg;

    private String sourceUrl;

    private String sourceType;

    private String status;

    private String reviewedAt;

    private String tag;
}
