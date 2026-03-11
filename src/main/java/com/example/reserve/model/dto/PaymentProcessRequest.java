package com.example.reserve.model.dto;

import com.example.reserve.model.enums.FailureReason;
import com.example.reserve.model.enums.PaymentsStatus;

import java.math.BigDecimal;

public record PaymentProcessRequest(
        String pgTxId,
        String idemKey,
        BigDecimal amount,
        PaymentsStatus status,
        FailureReason failureReason
) {
}