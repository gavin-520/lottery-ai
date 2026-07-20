package com.lottery.domain;

/**
 * Supported lottery games. SSQ (双色球) remains the legacy default in persisted SSQ tables;
 * platform UI/default type can be configured independently (e.g. FC3D).
 */
public enum LotteryType {
    SSQ("双色球"),
    FC3D("福彩3D");

    private final String displayName;

    LotteryType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static LotteryType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return SSQ;
        }
        return LotteryType.valueOf(code.trim().toUpperCase());
    }
}
