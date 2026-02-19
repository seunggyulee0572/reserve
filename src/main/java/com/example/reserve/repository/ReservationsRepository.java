package com.example.reserve.repository;

import com.example.reserve.entity.Reservations;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReservationsRepository extends JpaRepository<Reservations, UUID> {

    int countByEvent_IdAndSeats_Id( UUID eventId, UUID seatsId );
}
