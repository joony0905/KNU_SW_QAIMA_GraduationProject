package com.qaima.dto.user;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FindIdRequestDto {

    @NotBlank(message = "이름은 필수입니다.")
    private String name;

    private String phone;

    @NotBlank(message = "생년월일은 필수입니다.")
    private String birthdate;
}
