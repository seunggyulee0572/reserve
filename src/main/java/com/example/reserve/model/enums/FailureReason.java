package com.example.reserve.model.enums;

public enum FailureReason {
    AMOUNT_MISMATCH,   // 재시도 X — 사용자/PG 문제
    PG_ERROR,          // 재시도 O — PG 일시 장애
    TIMEOUT            // 재시도 O — 네트워크 문제
}