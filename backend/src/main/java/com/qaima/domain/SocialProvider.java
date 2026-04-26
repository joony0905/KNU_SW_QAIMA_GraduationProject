package com.qaima.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum SocialProvider {
    GOOGLE("google"),
    KAKAO("kakao"),
    NAVER("naver");

    private final String registrationId;

    SocialProvider(String registrationId) {
        this.registrationId = registrationId;
    }

    public String getRegistrationId() {
        return registrationId;
    }

    public static Optional<SocialProvider> fromRegistrationId(String registrationId) {
        if (registrationId == null || registrationId.isBlank()) {
            return Optional.empty();
        }
        String normalized = registrationId.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(provider -> provider.registrationId.equals(normalized))
                .findFirst();
    }
}
