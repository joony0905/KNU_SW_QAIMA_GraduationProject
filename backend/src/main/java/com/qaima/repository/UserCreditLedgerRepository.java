package com.qaima.repository;

import com.qaima.domain.UserCreditLedger;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCreditLedgerRepository extends JpaRepository<UserCreditLedger, Long> {

    List<UserCreditLedger> findByUserUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
