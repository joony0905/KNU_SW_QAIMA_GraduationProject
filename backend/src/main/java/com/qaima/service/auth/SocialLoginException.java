package com.qaima.service.auth;

import lombok.Getter;

@Getter
public class SocialLoginException extends RuntimeException {

    private final String code;

    public SocialLoginException(String code, String message) {
        super(message);
        this.code = code;
    }
}
