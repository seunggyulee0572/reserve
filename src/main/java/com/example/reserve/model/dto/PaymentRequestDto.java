package com.example.reserve.model.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequestDto(
        UUID reservationId,
        BigDecimal amount,
        String idemKey
) {
}