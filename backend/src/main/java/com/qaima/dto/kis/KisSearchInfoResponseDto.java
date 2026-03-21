package com.qaima.dto.kis;

import com.qaima.external.KisRtHeader;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KisSearchInfoResponseDto implements KisRtHeader {

    private String rt_cd;
    private String msg_cd;
    private String msg1;
    private Output output;

    @Getter
    @Setter
    public static class Output {

        // ===== 기본 =====
        private String pdno;                 // 상품번호
        private String prdt_name;            // 상품명
        private String prdt_abrv_name;       // 상품약칭 (삼성전자)
        private String prdt_eng_name;        // 영문명

        // ===== 지수 편입 여부 =====
        private String kospi200_item_yn;     // 코스피200 편입 여부 (Y/N)

        // ===== KRX 업종 체계 =====
        private String idx_bztp_lcls_cd;     // 대분류
        private String idx_bztp_lcls_cd_name;

        private String idx_bztp_mcls_cd;     // 중분류 (Sector)
        private String idx_bztp_mcls_cd_name;

        private String idx_bztp_scls_cd;     // 소분류 (Industry)
        private String idx_bztp_scls_cd_name;

        // ===== 보조 =====
        private String std_idst_clsf_cd;     // 표준산업분류
        private String std_idst_clsf_cd_name;
    }
}