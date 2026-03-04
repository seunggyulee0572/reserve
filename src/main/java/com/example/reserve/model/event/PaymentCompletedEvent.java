package com.example.reserve.model.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentCompletedEvent(
    UUID paymentId,
    UUID eventId,
    UUID reservationId,
    BigDecimal amount,
    LocalDateTime completedAt
) {}