package com.qaima.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PwdResetRequestDto {
    @NotBlank
    private String token;

    @NotBlank
    private String newPassword;
}
