package com.qaima.service.auth;

import com.qaima.common.Blocking;
import com.qaima.common.ErrorCode;
import com.qaima.common.ErrorException;
import com.qaima.domain.LoginSession;
import com.qaima.domain.User;
import com.qaima.repository.LoginSessionRepository;
import com.qaima.security.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class LoginSessionService {

    private static final int REFRESH_TOKEN_BYTES = 64;

    private final SecureRandom secureRandom = new SecureRandom();
    private final LoginSessionRepository loginSessionRepository;
    private final JwtProperties jwtProperties;

    public Mono<String> issueRefreshToken(User user, String ip, String userAgent, String deviceId) {
        if (user == null || user.getUserId() == null) {
            return Mono.error(new IllegalArgumentException("user is required"));
        }

        String refreshToken = newRefreshToken();
        String refreshTokenHash = hashRefreshToken(refreshToken);
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(Math.max(60, jwtProperties.getRefreshTokenValiditySeconds()));

        LoginSession session = new LoginSession();
        session.setUser(user);
        session.setDeviceId(deviceId);
        session.setIp(ip);
        session.setUserAgent(userAgent);
        session.setRefreshTokenHash(refreshTokenHash);
        session.setExpiresAt(expiresAt);

        return Blocking.call(() -> loginSessionRepository.save(session))
                .thenReturn(refreshToken);
    }

    public Mono<RotateResult> rotateRefreshToken(String refreshToken, String ip, String userAgent) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Mono.error(new ErrorException(ErrorCode.INVALID_TOKEN, "refreshToken is required"));
        }

        String refreshTokenHash = hashRefreshToken(refreshToken);

        return Blocking.call(() -> loginSessionRepository.findByRefreshTokenHashWithUser(refreshTokenHash)
                        .orElseThrow(() -> new ErrorException(ErrorCode.INVALID_TOKEN, "Invalid refresh token")))
                .flatMap(session -> {
                    Instant now = Instant.now();
                    if (session.getRevokedAt() != null) {
                        return Mono.error(new ErrorException(ErrorCode.INVALID_TOKEN, "Refresh token has been revoked"));
                    }
                    if (session.getExpiresAt() == null || !session.getExpiresAt().isAfter(now)) {
                        return Mono.error(new ErrorException(ErrorCode.INVALID_TOKEN, "Refresh token has expired"));
                    }

                    String newRefreshToken = newRefreshToken();
                    session.setRefreshTokenHash(hashRefreshToken(newRefreshToken));
                    session.setExpiresAt(now.plusSeconds(Math.max(60, jwtProperties.getRefreshTokenValiditySeconds())));
                    session.setIp(ip);
                    session.setUserAgent(userAgent);

                    return Blocking.call(() -> loginSessionRepository.save(session))
                            .thenReturn(new RotateResult(session.getUser(), newRefreshToken));
                });
    }

    public Mono<Void> revokeByRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Mono.empty();
        }

        String refreshTokenHash = hashRefreshToken(refreshToken);

        return Blocking.call(() -> loginSessionRepository.findByRefreshTokenHashWithUser(refreshTokenHash))
                .flatMap(optional -> {
                    if (optional.isEmpty()) {
                        return Mono.empty();
                    }

                    LoginSession session = optional.get();
                    if (session.getRevokedAt() != null) {
                        return Mono.empty();
                    }

                    session.setRevokedAt(Instant.now());
                    return Blocking.call(() -> loginSessionRepository.save(session)).then();
                });
    }

    public record RotateResult(User user, String refreshToken) {}

    private String newRefreshToken() {
        byte[] buffer = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    private String hashRefreshToken(String refreshToken) {
        String secret = jwtProperties.getSecretKey();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("jwt.secret-key is required for refresh token hashing");
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] output = mac.doFinal(refreshToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(output);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash refresh token", e);
        }
    }
}
