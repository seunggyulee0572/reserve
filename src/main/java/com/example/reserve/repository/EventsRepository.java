package com.example.reserve.repository;

import com.example.reserve.entity.Events;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface EventsRepository extends JpaRepository<Events, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update Events e
           set e.availableSeats = e.availableSeats - 1
         where e.id = :eventId
           and e.availableSeats > 0
        """)
    int decreaseIfAvailable(@Param("eventId") UUID eventId);
}
