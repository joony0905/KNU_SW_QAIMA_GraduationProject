package com.qaima.dto.user;

import com.qaima.domain.InvestmentLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserProfileUpdateRequestDto {

    private String name;
    private String phone;
    private String experience;
    private Boolean glossaryHover;
    private InvestmentLevel investmentLevel;
}
