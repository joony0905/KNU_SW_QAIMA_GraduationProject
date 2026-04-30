package com.qaima.dto.kis;

import com.qaima.external.KisRtHeader;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KisInvestorDailyByMarketResponseDto implements KisRtHeader {
    private String rt_cd;
    private String msg_cd;
    private String msg1;
    private List<Row> output;

    @Getter
    @Setter
    public static class Row {
        private String stck_bsop_date;
        private String frgn_ntby_qty;
        private String frgn_ntby_tr_pbmn;
        private String prsn_ntby_qty;
        private String prsn_ntby_tr_pbmn;
        private String orgn_ntby_qty;
        private String orgn_ntby_tr_pbmn;
    }
}
