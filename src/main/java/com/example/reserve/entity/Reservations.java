package com.example.reserve.entity;

import com.example.reserve.model.enums.ReservationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "reservations")
public class Reservations extends BaseEntity{

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
    private BigDecimal totalAmount;
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
