package com.qaima.repository;

import com.qaima.domain.AuthLoginLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthLoginLogRepository extends JpaRepository<AuthLoginLog, Long> {

}
