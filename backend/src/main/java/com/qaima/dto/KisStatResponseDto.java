package com.qaima.dto;

import lombok.Data;

@Data
public class KisStatResponseDto {

    // 처리 결과 코드 ("0" 이면 정상)
    private String rt_cd;

    // 상세 코드 (정상: MCA00000)
    private String msg_cd;

    // 메시지 ("정상처리 되었습니다." 등)
    private String msg1;

    // 시세 정보 본문
    private Output output;

    @Data
    public static class Output {

        // 종목 상태 코드 (정상/관리/투자주의 등)
        private String iscd_stat_cls_code;

        // 증거금률 (%)
        private String marg_rate;

        // 대표 시장 한글명 (예: KOSPI200)
        private String rprs_mrkt_kor_name;

        // 업종 한글명 (예: 전기·전자)
        private String bstp_kor_isnm;

        // 일시 정지 여부 (Y/N)
        private String temp_stop_yn;

        // 시가 기준 가격제한폭 도달 여부 (Y/N)
        private String oprc_rang_cont_yn;

        // 종가 기준 가격제한폭 도달 여부 (Y/N)
        private String clpr_rang_cont_yn;

        // 신용거래 가능 여부
        private String crdt_able_yn;

        // 보증금률 분류 코드
        private String grmn_rate_cls_code;

        // ELW 발행 여부
        private String elw_pblc_yn;

        // 현재가
        private String stck_prpr;

        // 전일 대비 가격
        private String prdy_vrss;

        // 전일 대비 부호 (1:상승,2:하락 등)
        private String prdy_vrss_sign;

        // 전일 대비율 (%)
        private String prdy_ctrt;

        // 누적 거래대금
        private String acml_tr_pbmn;

        // 누적 거래량
        private String acml_vol;

        // 전일 대비 거래량 증감률 (%)
        private String prdy_vrss_vol_rate;

        // 시가
        private String stck_oprc;

        // 고가
        private String stck_hgpr;

        // 저가
        private String stck_lwpr;

        // 상한가
        private String stck_mxpr;

        // 하한가
        private String stck_llam;

        // 기준가(전일 종가)
        private String stck_sdpr;

        // 가중평균 주가
        private String wghn_avrg_stck_prc;

        // 외국인 보유비율 (%)
        private String hts_frgn_ehrt;

        // 외국인 순매수 수량
        private String frgn_ntby_qty;

        // 프로그램 매매 순매수 수량
        private String pgtr_ntby_qty;

        // 2차 매수 호가
        private String pvt_scnd_dmrs_prc;

        // 1차 매수 호가
        private String pvt_frst_dmrs_prc;

        // 기준 포인트 값
        private String pvt_pont_val;

        // 1차 매도 호가
        private String pvt_frst_dmsp_prc;

        // 2차 매도 호가
        private String pvt_scnd_dmsp_prc;

        // 매수 호가 평균 값
        private String dmrs_val;

        // 매도 호가 평균 값
        private String dmsp_val;

        // 자본금 (억 단위 등)
        private String cpfn;

        // 상/하한가 폭
        private String rstc_wdth_prc;

        // 액면가
        private String stck_fcam;

        // 기준가(호가단위 기준값)
        private String stck_sspr;

        // 호가 단위
        private String aspr_unit;

        // HTS 거래 단위 (최소 거래 수량)
        private String hts_deal_qty_unit_val;

        // 상장 주식 수
        private String lstn_stcn;

        // HTS 시가총액 (억/조 단위)
        private String hts_avls;

        // PER
        private String per;

        // PBR
        private String pbr;

        // 결산월 (예: 12)
        private String stac_month;

        // 회전율 (거래량/상장주식수)
        private String vol_tnrt;

        // EPS
        private String eps;

        // BPS
        private String bps;

        // 250일 최고가
        private String d250_hgpr;

        // 250일 최고가 발생일
        private String d250_hgpr_date;

        // 250일 최고가 대비 현 주가 등락률
        private String d250_hgpr_vrss_prpr_rate;

        // 250일 최저가
        private String d250_lwpr;

        // 250일 최저가 발생일
        private String d250_lwpr_date;

        // 250일 최저가 대비 현 주가 등락률
        private String d250_lwpr_vrss_prpr_rate;

        // 당해연도 최고가
        private String stck_dryy_hgpr;

        // 당해연도 최고가 대비 현 주가 등락률
        private String dryy_hgpr_vrss_prpr_rate;

        // 당해연도 최고가 발생일
        private String dryy_hgpr_date;

        // 당해연도 최저가
        private String stck_dryy_lwpr;

        // 당해연도 최저가 대비 현 주가 등락률
        private String dryy_lwpr_vrss_prpr_rate;

        // 당해연도 최저가 발생일
        private String dryy_lwpr_date;

        // 52주 최고가
        private String w52_hgpr;

        // 52주 최고가 대비 현 주가 등락률
        private String w52_hgpr_vrss_prpr_ctrt;

        // 52주 최고가 발생일
        private String w52_hgpr_date;

        // 52주 최저가
        private String w52_lwpr;

        // 52주 최저가 대비 현 주가 등락률
        private String w52_lwpr_vrss_prpr_ctrt;

        // 52주 최저가 발생일
        private String w52_lwpr_date;

        // 담보 비율 (대출 가능 비율 등)
        private String whol_loan_rmnd_rate;

        // 공시/투자주의/정지 종목 여부
        private String ssts_yn;

        // 단축 종목코드 (6자리)
        private String stck_shrn_iscd;

        // 액면가 표시 문자열
        private String fcam_cnnm;

        // 자본금 표시 문자열 (단위 포함)
        private String cpfn_cnnm;

        // 외국인 보유 주식 수
        private String frgn_hldn_qty;

        // VI 발동 여부
        private String vi_cls_code;

        // 시간외 VI 발동 여부
        private String ovtm_vi_cls_code;

        // 직전 체결 수량
        private String last_ssts_cntg_qty;

        // 투자 유의/주의 종목 여부
        private String invt_caful_yn;

        // 시장 경고 코드
        private String mrkt_warn_cls_code;

        // 공매도 과열 여부
        private String short_over_yn;

        // 거래 정지 여부
        private String sltr_yn;

        // 관리 종목 여부
        private String mang_issu_cls_code;
    }
}
