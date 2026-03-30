package com.qaima.dto.opendart;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OpenDartStockTotalStatusResponse {

    private String status;       // OpenDART 응답 코드(000: 정상)
    private String message;      // OpenDART 응답 메시지
    private List<Row> list;      // 발행주식수 상세 목록

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Row {

        // 공시 접수번호
        @JsonProperty("rcept_no")
        private String rceptNo;

        // 법인 구분
        @JsonProperty("corp_cls")
        private String corpCls;

        // OpenDART 회사 고유 코드
        @JsonProperty("corp_code")
        private String corpCode;

        // 회사명
        @JsonProperty("corp_name")
        private String corpName;
        private String se;     // 주식 구분(보통주, 우선주, 합계 등)

        // 발행할 주식의 총수
        @JsonProperty("isu_stock_totqy")
        private String isuStockTotqy;

        // 현재까지 발행한 주식의 총수
        @JsonProperty("now_to_isu_stock_totqy")
        private String nowToIsuStockTotqy;

        // 현재까지 감소한 주식의 총수
        @JsonProperty("now_to_dcrs_stock_totqy")
        private String nowToDcrsStockTotqy;
        private String redc;        // 감자로 감소한 주식수

        // 이익소각으로 감소한 주식수
        @JsonProperty("profit_incnr")
        private String profitIncnr;

        // 상환으로 감소한 주식수
        @JsonProperty("rdmstk_repy")
        private String rdmstkRepy;
        private String etc;        // 기타 사유로 감소한 주식수

        // 발행주식의 총수
        @JsonProperty("istc_totqy")
        private String istcTotqy;

        // 자기주식수
        @JsonProperty("tesstk_co")
        private String tesstkCo;

        // 유통주식수
        @JsonProperty("distb_stock_co")
        private String distbStockCo;

        // 결산기준일
        @JsonProperty("stlm_dt")
        private String stlmDt;
    }
}
