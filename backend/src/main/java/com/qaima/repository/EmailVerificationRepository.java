package com.qaima.repository;

import com.qaima.domain.EmailVerification;
import com.qaima.domain.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {
    Optional<EmailVerification> findByTokenHash(String tokenHash);
    Optional<EmailVerification> findByUserAndTokenHash(User user, String tokenHash);
    Optional<EmailVerification> findByEmailAndTokenHash(String email, String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailVerification> findFirstByEmailAndUsedAtIsNotNullAndExpiresAtAfterOrderByUsedAtDescCreatedAtDesc(
            String email,
            Instant now
    );

    long deleteByUser(User user);
    long deleteByEmail(String email);

    boolean existsByUserAndCreatedAtAfter(User user, Instant after);
    boolean existsByEmailAndCreatedAtAfter(String email, Instant after);
}
