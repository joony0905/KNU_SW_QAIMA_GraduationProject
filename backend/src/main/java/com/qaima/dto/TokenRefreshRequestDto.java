package com.qaima.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TokenRefreshRequestDto {
    @NotBlank(message = "refreshToken is required")
    private String refreshToken;
}

