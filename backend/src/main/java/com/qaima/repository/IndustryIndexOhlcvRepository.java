package com.qaima.repository;

import com.qaima.domain.IndustryIndexOhlcv;
import com.qaima.domain.IndustryIndexOhlcvId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IndustryIndexOhlcvRepository extends JpaRepository<IndustryIndexOhlcv, IndustryIndexOhlcvId> {
}
