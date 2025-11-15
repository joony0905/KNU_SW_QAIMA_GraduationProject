package com.qaima.repository;

import com.qaima.domain.Industry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface IndustryRepository extends JpaRepository<Industry, Long> {
    Optional<Industry> findByName(String name);
}
