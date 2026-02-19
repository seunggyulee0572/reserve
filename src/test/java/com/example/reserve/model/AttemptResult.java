package com.example.reserve.model;

public record AttemptResult(
        int idx,
        boolean success,
        String reservationId,
        String errorType,
        String errorMsg,
        long tookMs
) {}