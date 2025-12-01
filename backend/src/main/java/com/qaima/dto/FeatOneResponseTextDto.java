package com.qaima.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatOneResponseTextDto {

    // LLM이 생성한 분석 텍스트
    private String analysisText;

    //TODO 나중에 세부 섹션(요약/리스크/투자포인트 등) 쪼개고 구체화할때 필드 추가
}
