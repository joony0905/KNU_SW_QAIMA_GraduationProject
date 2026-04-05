package com.qaima.dto.user;

import lombok.Getter;

@Getter
public class LoginResponseDto {

    private final Long userId;
    private final String email;
    private final String name;
    private final String accessToken;

    public LoginResponseDto(Long userId, String email, String name, String accessToken) {
        this.userId = userId;
        this.email = email;
        this.name = name;
        this.accessToken = accessToken;
    }
}
