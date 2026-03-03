package com.qaima.dto.featone;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatOneResponseTextDto {

    private Long stockId;       // 선택사항
    private String stockCode;   // 선택사항

    private String summary;     // 한 줄 요약
    private String business;    // 사업/비즈니스 설명
    private String financial;   // 재무 상태/지표 해석
    private String valuation;   // 밸류에이션/밴드 설명
    private String risk;        // 리스크 요인
    private String outlook;     // 전망/코멘트

    // 프롬프트 디버깅용
    private String rawPrompt;

    // summary~outlook 전부 합친 완성본 텍스트 (편의용)
    private String analysisText;
}
