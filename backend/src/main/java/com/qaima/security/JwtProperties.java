package com.qaima.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "jwt")

public class JwtProperties {

    // application.yml의 jwt.secret-key 값
    private String secretKey;

    // application.yml의 jwt.access-token-validity-seconds 값
    private long accessTokenValiditySeconds;

    // 선택 값이며 미설정 시 기본 30일 사용
    // application.yml의 jwt.refresh-token-validity-seconds 값
    private long refreshTokenValiditySeconds = 60L * 60L * 24L * 30L;
}
