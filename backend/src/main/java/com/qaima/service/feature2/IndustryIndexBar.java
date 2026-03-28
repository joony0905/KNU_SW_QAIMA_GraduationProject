package com.qaima.service.feature2;

import com.qaima.domain.Freq;
import com.qaima.domain.IndustryIndex;
import com.qaima.domain.IndustryIndexOhlcv;
import com.qaima.domain.IndustryIndexOhlcvId;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Builder
public class IndustryIndexBar {

    private OffsetDateTime ts;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private BigDecimal volume;

    public IndustryIndexOhlcv toEntity(IndustryIndex industryIndex, Freq freq) {

        if (industryIndex == null || industryIndex.getIndexId() == null) {
            throw new IllegalStateException("industryIndex is not persisted");
        }

        if (ts == null || open == null || high == null || low == null || close == null) {
            throw new IllegalArgumentException("Invalid OHLC data");
        }

        IndustryIndexOhlcv entity = new IndustryIndexOhlcv();

        IndustryIndexOhlcvId id = new IndustryIndexOhlcvId();
        id.setIndexId(industryIndex.getIndexId());
        id.setTs(ts);
        id.setFreq(freq);

        entity.setId(id);
        entity.setIndustryIndex(industryIndex);

        entity.setOpen(open);
        entity.setHigh(high);
        entity.setLow(low);
        entity.setClose(close);
        entity.setVolume(volume != null ? volume : BigDecimal.ZERO);

        return entity;
    }
}