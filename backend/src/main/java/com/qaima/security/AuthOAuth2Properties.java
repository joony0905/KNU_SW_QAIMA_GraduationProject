package com.qaima.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "auth.oauth2")
public class AuthOAuth2Properties {

    private String successRedirectUrl = "http://localhost:5173/login/oauth2/success";
    private String failureRedirectUrl = "http://localhost:5173/login";
}
