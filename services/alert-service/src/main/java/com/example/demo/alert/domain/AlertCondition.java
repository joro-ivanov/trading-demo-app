package com.example.demo.alert.domain;

import java.math.BigDecimal;

/** When an alert fires relative to its threshold. Both bounds are inclusive. */
public enum AlertCondition {

    ABOVE {
        @Override
        public boolean isMetBy(BigDecimal price, BigDecimal threshold) {
            return price.compareTo(threshold) >= 0;
        }
    },
    BELOW {
        @Override
        public boolean isMetBy(BigDecimal price, BigDecimal threshold) {
            return price.compareTo(threshold) <= 0;
        }
    };

    public abstract boolean isMetBy(BigDecimal price, BigDecimal threshold);
}
