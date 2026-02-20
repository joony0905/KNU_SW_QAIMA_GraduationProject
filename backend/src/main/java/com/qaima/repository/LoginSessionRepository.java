package com.qaima.repository;

import com.qaima.domain.LoginSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LoginSessionRepository extends JpaRepository<LoginSession, Long> {

    @Query("""
        select s from LoginSession s
        join fetch s.user u
        where s.refreshTokenHash = :refreshTokenHash
    """)
    Optional<LoginSession> findByRefreshTokenHashWithUser(@Param("refreshTokenHash") String refreshTokenHash);
}

