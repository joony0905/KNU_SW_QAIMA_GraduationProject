package com.qaima.domain;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "users")
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(length = 100)
    private String name;

    @Column(unique = true, length = 30)
    private String phone;

    @Column(length = 20)
    private String experience;

    @Column(length = 6)
    private String birthdate;

    // --- 권한/상태 ---
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @ColumnDefault("'user'")
    private UserRole role;

    @Column(nullable = false, length = 20)
    @ColumnDefault("'active'")
    private String status;

    // --- 이메일 인증 ---
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean emailVerified;

    private Instant emailVerifiedAt;

    // --- 로그인 보안 ---
    private Instant lastLoginAt;
    @Column(length = 45)
    private String lastLoginIp;
    private Instant lockedUntil;

    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean glossaryHover;


    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

}
