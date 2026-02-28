package com.example.reserve.model.dto;

import java.math.BigDecimal;
import java.util.UUID;

public interface ReservationForPayment {

    UUID getReservationId();
    UUID getSeatId();
    String getSeatNumber();
    String getUserId();
    BigDecimal getTotalAmount();
}
