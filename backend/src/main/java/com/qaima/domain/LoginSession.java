package com.qaima.domain;

import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "login_session")
@Getter
@Setter
@NoArgsConstructor
public class LoginSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 100)
    private String deviceId;

    @Column(length = 45)
    private String ip;

    @Column(length = 255)
    private String userAgent;

    @Column(unique = true, length = 200)
    private String refreshTokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
