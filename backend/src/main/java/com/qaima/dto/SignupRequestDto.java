package com.qaima.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class SignupRequestDto {


    private String email;
    private String password;
    private String name;
    private String birthdate;
    private String phone;

}
