package com.example.reserve.entity;

import com.example.reserve.model.enums.PaymentsStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table("payments")
public class Payments extends BaseEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn("reservation_id")
    private Reservations reservation;
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    private PaymentsStatus status;
    private String paymentKey;
    private String idempotencyKey;
    private int retryCount;
    @LastModifiedDate
    private LocalDateTime updatedAt;

}
