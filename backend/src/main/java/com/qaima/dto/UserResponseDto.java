package com.qaima.dto;

import com.qaima.domain.User;
import lombok.Getter;

@Getter
  
public class UserResponseDto {

    private Long userId;
    private String email;
    private String name;
    private String phone;
    private String birthdate;

    public UserResponseDto(User user) {
        this.userId = user.getUserId();
        this.email = user.getEmail();
        this.name = user.getName();
        this.phone = user.getPhone();
        this.birthdate = user.getBirthdate();
    }
}
