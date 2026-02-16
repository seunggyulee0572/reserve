package com.example.reserve.entity;

import com.example.reserve.model.enums.SeatStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
public class Seats {

    @Id
    @GeneratedValue( strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn( name = "event_id")
    private Events event;

    private String seatNumber;

    @Enumerated(EnumType.STRING)
    private SeatStatus seatStatus;

    private LocalDateTime reservedAt;
    private String reservedBy;
    private Integer version;

}


