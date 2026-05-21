package com.qaima.repository;

import com.qaima.domain.IndustryIndex;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IndustryIndexRepository extends JpaRepository<IndustryIndex, Long> {
    Optional<IndustryIndex> findByCode(String code);
}
