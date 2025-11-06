package com.qaima.domain;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // bigserial [pk] -> BIGINT AUTO_INCREMENT
    private Long userId;

    @Column(nullable = false, unique = true, length = 255) // email varchar(255) [not null, unique]
    private String email;

    @Column(nullable = false, length = 255) // password_hash varchar(255) [not null]
    private String passwordHash;

    @Column(length = 100)
    private String name;

    @Column(unique = true, length = 30)
    private String phone;

    @Column(length = 20)
    private String experience;

    // --- 권한/상태 ---
    @Enumerated(EnumType.STRING) // Enum -> varchar
    @Column(nullable = false, length = 20)
    @ColumnDefault("'user'") // user_role [not null, default: 'user']
    private UserRole role;

    @Column(nullable = false, length = 20)
    @ColumnDefault("'active'") // status [not null, default: 'active']
    private String status;

    // 이메일 인증
    @Column(nullable = false)
    @ColumnDefault("false") // email_verified bool [not null, default: false]
    private boolean emailVerified;

    private Instant emailVerifiedAt; // timestamptz -> Instant (UTC)

    // 로그인 보안
    private Instant lastLoginAt;
    @Column(length = 45)
    private String lastLoginIp;
    private Instant lockedUntil;

    // UI 설정
    @Column(nullable = false)
    @ColumnDefault("false") // glossary_hover bool [not null, default: false]
    private boolean glossaryHover;

    // 타임스탬프
    @CreationTimestamp // default: 'now()' -> @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp // default: 'now()' -> @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
    
}
