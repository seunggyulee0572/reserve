package com.example.reserve.repository;

import com.example.reserve.entity.Seats;
import com.example.reserve.model.enums.SeatStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.swing.text.html.Option;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface SeatsRepository extends JpaRepository<Seats, UUID> {


    Optional<Seats> findByEvent_IdAndSeatNumber( UUID eventId, String seatNumber );
    Optional<Seats> findByEvent_IdAndSeatNumberAndSeatStatus(UUID eventId, String seatNumber, SeatStatus seatStatus);

//    @Lock(LockModeType.PESSIMISTIC_WRITE)
//    @Query("""
//        select s from Seats s
//        where s.event.id = :eventId
//          and s.seatNumber = :seatNumber
//    """)
//    int reserveIfAvailable(UUID eventId, String seatNumber);


    @Modifying
    @Query("""
        update Seats s
           set s.seatStatus = 'RESERVED',
               s.reservedBy = :userId,
               s.reservedAt = :now
         where s.event.id = :eventId
           and s.seatNumber = :seatNumber
           and s.seatStatus = 'AVAILABLE'
    """)
    int reserveIfAvailable(UUID eventId, String seatNumber, String userId, LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update Seats s
           set s.seatStatus = com.example.reserve.model.enums.SeatStatus.AVAILABLE,
               s.reservedBy = null,
               s.reservedAt = null
         where s.event.id = :eventId
           and s.seatNumber = :seatNumber
           and s.reservedBy = :userId
           and s.seatStatus = com.example.reserve.model.enums.SeatStatus.RESERVED
        """)
    int rollbackReservedSeat(@Param("eventId") UUID eventId,
                             @Param("seatNumber") String seatNumber,
                             @Param("userId") String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select s from Seats s
        where s.event.id = :eventId
          and s.seatNumber = :seatNumber
    """)
    Optional<Seats> findForUpdate(UUID eventId, String seatNumber);

    Optional<Seats> findByEvent_IdAndSeatNumberAndReservedBy(UUID eventId, String seatNumber, String reservedBy);

}
