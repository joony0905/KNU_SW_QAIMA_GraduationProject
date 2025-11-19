package com.qaima.dto;

import lombok.Getter;

@Getter
public class LoginResponseDto {

    private Long userId;
    private String email;
    private String name;
    private String accessToken;

    public LoginResponseDto(Long userId, String email, String name, String accessToken) {
        this.userId = userId;
        this.email = email;
        this.name = name;
        this.accessToken = accessToken;
    }
}
