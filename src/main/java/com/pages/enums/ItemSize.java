package com.pages.enums;

import lombok.Getter;

@Getter
public enum ItemSize {
    SMALL_UP_10KG(1),
    MEDIUM_UP_20KG(2),
    LARGE_UP_30KG(3),
    XLARGE_UP_50KG(4),
    HEAVY_OVER_50KG(5);

    private final int level;

    ItemSize(int level) {
        this.level = level;
    }

}
