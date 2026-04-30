package com.qaima.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor

public class LoginRequestDto {
    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;
}
