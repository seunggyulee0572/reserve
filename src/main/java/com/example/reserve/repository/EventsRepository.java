package com.example.reserve.repository;

import com.example.reserve.entity.Events;
import jakarta.transaction.Transactional;
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

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE events e
           SET e.available_seats = LEAST(e.total_seats, e.available_seats + :delta)
         WHERE e.id = :eventId
        """, nativeQuery = true)
    int increaseAvailableSeats(@Param("eventId") UUID eventId, @Param("delta") int delta);
}
