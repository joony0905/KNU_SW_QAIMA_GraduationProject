package com.qaima.dto.feature2;

import com.qaima.common.Feat2WarningCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class Feature2MetaDto {

    private final List<String> warnings;

    public static Feature2MetaDto empty() {
        return Feature2MetaDto.builder()
                .warnings(new ArrayList<>())
                .build();
    }

    public void addWarning(Feat2WarningCode code) {
        if (code == null) return;
        warnings.add(code.name());
    }

    public void addWarning(String code) {
        if (code == null || code.isBlank()) return;
        warnings.add(code);
    }
}