package com.example.reserve.model.dto;

import java.util.UUID;

public interface ReservationRefs {
    UUID getEventId();
    UUID getSeatId();
    String getUserId();
}