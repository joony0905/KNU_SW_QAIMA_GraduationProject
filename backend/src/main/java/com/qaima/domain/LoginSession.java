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
    @GeneratedValue(strategy = GenerationType.IDENTITY) // bigserial [pk]
    private Long sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false) // user_id bigint [not null, ref: > users.user_id]
    private User user;

    @Column(length = 100)
    private String deviceId;

    @Column(length = 45)
    private String ip;

    @Column(length = 255)
    private String userAgent;

    @Column(unique = true, length = 200) // refresh_token_hash varchar(200) [unique]
    private String refreshTokenHash;

    @Column(nullable = false)
    private Instant expiresAt; // expires_at timestamptz [not null]

    private Instant revokedAt; // revoked_at timestamptz

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt; // created_at timestamptz [not null, default: 'now()']
}
