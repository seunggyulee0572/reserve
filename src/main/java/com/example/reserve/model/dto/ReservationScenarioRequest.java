package com.example.reserve.model.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ReservationScenarioRequest(
        UUID eventId,
        String seatNumber,
        String userId
) {
}