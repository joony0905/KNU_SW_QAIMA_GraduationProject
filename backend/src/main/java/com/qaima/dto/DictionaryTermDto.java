package com.qaima.dto;

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
    private String initial;
    private String description;
    private String source;
    private String tag;
    private Instant createdAt;
    private Instant updatedAt;
}

