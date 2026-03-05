package com.qaima.repository;

import com.qaima.domain.Sector;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SectorRepository extends JpaRepository<Sector, Long> {
    Optional<Sector> findBySchemeAndCode(String scheme, String code);
    Optional<Sector> findByName(String name); // fallback용(최후)
}