package com.example.reserve.entity;

import com.example.reserve.model.enums.ReservationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "reservations")
@EntityListeners(AuditingEntityListener.class)
public class Reservations {

    @Id
    @GeneratedValue( strategy = GenerationType.UUID)
    private UUID id;

    private String userId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn( name = "event_id")
    private Events event;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn( name = "seat_id")
    private Seats seats;
    @Enumerated(EnumType.STRING)
    private ReservationStatus status;
    private LocalDateTime expiresAt;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
