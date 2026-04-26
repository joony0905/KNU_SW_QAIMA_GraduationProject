package com.qaima.dto.dictionary;

import java.time.Instant;
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
public class DictionaryAliasDto {
    private String alias;
    private String canonicalTerm;
    private String sourceType;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
}
