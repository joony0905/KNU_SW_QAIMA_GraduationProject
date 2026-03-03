package com.qaima.dto.kis;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class KisResponseDto {
    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("expires_in")
    private int expiresIn;
}