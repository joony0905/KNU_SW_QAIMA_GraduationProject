package com.qaima.repository;

import com.qaima.domain.IndustryIndexMap;
import com.qaima.domain.IndustryIndexMapId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IndustryIndexMapRepository
        extends JpaRepository<IndustryIndexMap, IndustryIndexMapId> {

    @EntityGraph(attributePaths = "industryIndex")
    Optional<IndustryIndexMap> findFirstByIdIndustryId(Long industryId);
}
