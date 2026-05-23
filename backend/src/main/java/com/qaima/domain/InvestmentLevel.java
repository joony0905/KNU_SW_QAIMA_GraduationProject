package com.qaima.domain;

public enum InvestmentLevel {
    BEGINNER("초급자"),
    INTERMEDIATE("중급자"),
    ADVANCED("고급자"),
    EXPERT("전문가");

    private final String displayName;

    InvestmentLevel(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static InvestmentLevel fromDisplayName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        for (InvestmentLevel level : values()) {
            if (level.displayName.equals(value.trim())) {
                return level;
            }
        }
        return null;
    }
}
