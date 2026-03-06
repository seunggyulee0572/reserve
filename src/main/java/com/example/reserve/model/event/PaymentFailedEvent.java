package com.example.reserve.model.event;

import com.example.reserve.model.enums.FailureReason;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentFailedEvent(
    UUID paymentId,
    UUID reservationId,
    UUID eventId,
    BigDecimal amount,
    String idempotencyKey,
    FailureReason failureReason,
    int retryCount,
    Boolean retryable,
    LocalDateTime failedAt
) {}