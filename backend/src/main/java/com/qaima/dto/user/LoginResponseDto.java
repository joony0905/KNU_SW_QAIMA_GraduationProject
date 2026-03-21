package com.qaima.dto.user;

import lombok.Getter;

@Getter
public class LoginResponseDto {

    private final Long userId;
    private final String email;
    private final String name;
    private final String accessToken;
    private final String refreshToken;

    public LoginResponseDto(Long userId, String email, String name, String accessToken, String refreshToken) {
        this.userId = userId;
        this.email = email;
        this.name = name;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }
}
