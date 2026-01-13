package com.qaima.repository;

import com.qaima.domain.EmailVerification;
import com.qaima.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {
    Optional<EmailVerification> findByTokenHash(String tokenHash);
    Optional<EmailVerification> findByUserAndTokenHash(User user, String tokenHash);
    // 재발송 시 이전 토큰 무효화
    long deleteByUser(User user);

    // 최근 발급 토큰 존재 여부 확인
    boolean existsByUserAndCreatedAtAfter(User user, Instant after);

}
