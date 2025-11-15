package com.qaima.repository;

import com.qaima.domain.Exchange;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ExchangeRepository extends JpaRepository<Exchange, Long> {
    Optional<Exchange> findByCode(String code);
}
