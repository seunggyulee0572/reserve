package com.example.reserve.model.dto;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class ForPayment {

    private final UUID reservationId;
    private final UUID seatId;
    private final String userId;
    private final BigDecimal totalAmount;

    public ForPayment(UUID reservationId, UUID seatId, String userId, BigDecimal totalAmount) {
        this.reservationId = reservationId;
        this.seatId = seatId;
        this.userId = userId;
        this.totalAmount = totalAmount;
    }
}
