package com.qaima.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmailConfirmDto {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String code; // 6자리
}
