package com.qaima.dto.feature2;

import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
public class Feature2MetaDto {
    @Builder.Default
    private final List<String> warnings = new ArrayList<>();

    public void addWarning(String code) {
        if (code == null) return;
        warnings.add(code);
    }

    public void addWarning(Enum<?> code) {
        if (code == null) return;
        warnings.add(code.name());
    }
}