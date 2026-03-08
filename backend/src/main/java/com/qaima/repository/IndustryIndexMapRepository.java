package com.qaima.repository;

import com.qaima.domain.IndustryIndexMap;
import com.qaima.domain.IndustryIndexMapId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IndustryIndexMapRepository
        extends JpaRepository<IndustryIndexMap, IndustryIndexMapId> {

    Optional<IndustryIndexMap> findFirstByIdIndustryId(Long industryId);
}