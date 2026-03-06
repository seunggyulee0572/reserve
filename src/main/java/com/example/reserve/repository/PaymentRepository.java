package com.example.reserve.repository;

import com.example.reserve.entity.Payments;
import com.example.reserve.model.enums.PaymentsStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payments, UUID> {

    Optional<Payments> findPaymentsByIdempotencyKeyAndStatusIn(String idemPotencyKey, List<PaymentsStatus> status);

    Optional<Payments> findPaymentsByIdempotencyKey(String idemPotencyKey);

    Boolean existsPaymentsByIdempotencyKey( String IdempotencyKey);

    @Query("SELECT p FROM Payments p WHERE p.reservation.id = :reservationId " +
            "AND p.retryable = true AND p.status IN ('FAILED', 'RETRYING')")
    Optional<Payments> findRetryableFailedPayment(@Param("reservationId") UUID reservationId);
}
