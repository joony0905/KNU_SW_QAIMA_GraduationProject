package com.qaima.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "jwt")

public class JwtProperties {

    // application.yml: jwt.secret-key
    private String secretKey;

    // application.yml: jwt.access-token-validity-seconds
    private long accessTokenValiditySeconds;

    // application.yml: jwt.refresh-token-validity-seconds
    private long refreshTokenValiditySeconds = 60L * 60L * 24L * 30L;
}
