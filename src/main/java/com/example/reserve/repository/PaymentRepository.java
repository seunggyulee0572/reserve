package com.example.reserve.repository;

import com.example.reserve.entity.Payments;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payments, UUID> {

    Optional<Payments> findPaymentsByIdempotencyKey( String idemPotencyKey );

    Boolean existsPaymentsByIdempotencyKey( String IdempotencyKey);
}
