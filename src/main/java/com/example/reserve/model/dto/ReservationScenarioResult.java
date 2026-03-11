package com.example.reserve.model.dto;

import com.example.reserve.model.enums.ReservationStatus;
import com.example.reserve.model.enums.SeatStatus;

import java.util.UUID;

public record ReservationScenarioResult(
        UUID reservationId,
        UUID eventId,
        String seatNumber,
        String userId,
        SeatStatus seatStatus,
        ReservationStatus reservationStatus
) {
}