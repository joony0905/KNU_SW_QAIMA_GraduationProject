package com.qaima.security;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class RefreshTokenCookieService {

    private final AuthCookieProperties authCookieProperties;
    private final JwtProperties jwtProperties;

    public void writeRefreshTokenCookie(ServerHttpResponse response, String refreshToken) {
        response.addCookie(buildRefreshTokenCookie(
                refreshToken,
                Duration.ofSeconds(Math.max(60, jwtProperties.getRefreshTokenValiditySeconds()))
        ));
    }

    public void expireRefreshTokenCookie(ServerHttpResponse response) {
        response.addCookie(buildRefreshTokenCookie("", Duration.ZERO));
    }

    private ResponseCookie buildRefreshTokenCookie(String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(
                        authCookieProperties.getRefreshTokenName(),
                        value == null ? "" : value
                )
                .httpOnly(authCookieProperties.isHttpOnly())
                .secure(authCookieProperties.isSecure())
                .path(authCookieProperties.getPath())
                .maxAge(maxAge)
                .sameSite(authCookieProperties.getSameSite());

        if (StringUtils.hasText(authCookieProperties.getDomain())) {
            builder.domain(authCookieProperties.getDomain());
        }

        return builder.build();
    }
}
