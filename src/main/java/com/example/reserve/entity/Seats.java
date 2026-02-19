package com.example.reserve.entity;

import com.example.reserve.model.enums.SeatStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "seats")
public class Seats {

    @Id
    @GeneratedValue( strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn( name = "event_id")
    private Events event;

    private String seatNumber;

    @Enumerated(EnumType.STRING)
    @Column( name = "status")
    private SeatStatus seatStatus;

    private LocalDateTime reservedAt;
    private String reservedBy;
    @Version
    private Integer version;

}


