package com.qaima.dto.user;

import com.qaima.domain.User;
import lombok.Getter;

@Getter
  
public class UserResponseDto {

    private Long userId;
    private String email;
    private String name;
    private String phone;
    private String birthdate;
    private String experience;
    private boolean glossaryHover;

    public UserResponseDto(User user) {
        this.userId = user.getUserId();
        this.email = user.getEmail();
        this.name = user.getName();
        this.phone = user.getPhone();
        this.birthdate = user.getBirthdate();
        this.experience = user.getExperience();
        this.glossaryHover = user.isGlossaryHover();
    }
}
