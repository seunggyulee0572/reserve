package com.example.reserve.model.enums;

import jakarta.persistence.GeneratedValue;
import lombok.Getter;

@Getter
public enum ReservationStatus {

    PENDING,
    CONFIRMED,
    CANCELLED,
    EXPIRED
    ;
}
