package com.qaima.dto.user;

import lombok.Getter;

@Getter
public class TokenRefreshResponseDto {

    private final String accessToken;

    public TokenRefreshResponseDto(String accessToken) {
        this.accessToken = accessToken;
    }
}
