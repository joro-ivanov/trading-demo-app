package com.example.demo.alert.domain;

/** An alert is ACTIVE until a price crosses its threshold, then TRIGGERED (terminal). */
public enum AlertStatus {
    ACTIVE,
    TRIGGERED
}
