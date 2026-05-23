package com.qaima.dto.user;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SocialProfileCompleteRequestDto {

    private String name;
    private String phone;
    private String birthdate;
    private String country;
}
