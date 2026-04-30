package com.qaima.dto.kis;

import com.qaima.external.KisRtHeader;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KisInvestorTradeByStockDailyResponseDto implements KisRtHeader {
    private String rt_cd;
    private String msg_cd;
    private String msg1;
    private Object output1;
    private List<Row> output2;

    @Getter
    @Setter
    public static class Row {
        private String stck_bsop_date;
        private String stck_clpr;
        private String acml_vol;
        private String acml_tr_pbmn;
        private String frgn_ntby_qty;
        private String frgn_ntby_tr_pbmn;
        private String prsn_ntby_qty;
        private String prsn_ntby_tr_pbmn;
        private String orgn_ntby_qty;
        private String orgn_ntby_tr_pbmn;
        private String scrt_ntby_qty;
        private String scrt_ntby_tr_pbmn;
        private String ivtr_ntby_qty;
        private String ivtr_ntby_tr_pbmn;
        private String pe_fund_ntby_vol;
        private String pe_fund_ntby_tr_pbmn;
        private String bank_ntby_qty;
        private String bank_ntby_tr_pbmn;
        private String insu_ntby_qty;
        private String insu_ntby_tr_pbmn;
        private String fund_ntby_qty;
        private String fund_ntby_tr_pbmn;
        private String etc_ntby_qty;
        private String etc_ntby_tr_pbmn;
    }
}
