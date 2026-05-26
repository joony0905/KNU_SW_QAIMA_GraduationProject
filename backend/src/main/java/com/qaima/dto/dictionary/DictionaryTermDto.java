package com.qaima.dto.dictionary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DictionaryTermDto {
    private String term;
    private String termEn;
    private String initial;
    private String description;
    private String descriptionEn;
    private String source;
    private String sourceOrg;
    private String sourceUrl;
    private String sourceType;
    private String status;
    private Instant reviewedAt;
    private String tag;
    private Instant createdAt;
    private Instant updatedAt;
}
