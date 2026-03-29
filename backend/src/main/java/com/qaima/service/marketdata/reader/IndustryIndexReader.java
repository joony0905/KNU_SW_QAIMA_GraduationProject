package com.qaima.service.marketdata.reader;

import com.qaima.domain.Freq;
import com.qaima.domain.IndustryIndex;
import com.qaima.dto.feature2.Feature2MetaDto;
import com.qaima.dto.industry.IndustryIndexBlockDto;
import reactor.core.publisher.Mono;

//IndustryIndex 캐싱 리더기, 이후 기능3에서 사용될 수 있기 때문에 feat2랑 패키지를 분리했음.

public interface IndustryIndexReader {

    Mono<IndustryIndexBlockDto> read(
            IndustryIndex index,
            Feature2MetaDto meta,
            Freq freq,
            int window
    );
}
