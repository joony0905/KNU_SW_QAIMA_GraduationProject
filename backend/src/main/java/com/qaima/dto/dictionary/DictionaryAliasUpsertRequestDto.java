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
public class DictionaryAliasUpsertRequestDto {

    @NotBlank(message = "alias is required")
    private String alias;

    @NotBlank(message = "canonicalTerm is required")
    private String canonicalTerm;

    private String sourceType;

    private String notes;
}
