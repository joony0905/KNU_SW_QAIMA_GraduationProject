package com.qaima.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "auth.cookie")
public class AuthCookieProperties {

    private String refreshTokenName = "qaima_refresh_token";
    private String path = "/api/v1/auth";
    private String sameSite = "Lax";
    private String domain;
    private boolean secure = false;
    private boolean httpOnly = true;
}
