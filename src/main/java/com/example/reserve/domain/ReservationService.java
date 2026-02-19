package com.example.reserve.domain;

import com.example.reserve.entity.Events;
import com.example.reserve.entity.Reservations;
import com.example.reserve.entity.Seats;
import com.example.reserve.model.enums.ReservationStatus;
import com.example.reserve.model.enums.SeatStatus;
import com.example.reserve.repository.EventsRepository;
import com.example.reserve.repository.ReservationsRepository;
import com.example.reserve.repository.SeatsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ReservationService {

    private final ReservationsRepository reservationsRepository;
    private final SeatsRepository seatsRepository;
    private final EventsRepository eventsRepository;

    public ReservationService(ReservationsRepository reservationsRepository, SeatsRepository seatsRepository, EventsRepository eventsRepository) {
        this.reservationsRepository = reservationsRepository;
        this.seatsRepository = seatsRepository;
        this.eventsRepository = eventsRepository;
    }

    // todo : token에서 user 가져옴
    @Transactional
    public UUID generateReservation(UUID eventId, String seatNumber, String userId) {

        Seats seat = seatsRepository.findForUpdate(eventId, seatNumber)
                .orElseThrow(() -> new IllegalArgumentException("seat not found"));

        if (seat.getSeatStatus() != SeatStatus.AVAILABLE) {
            throw new IllegalStateException("already reserved");
        }

        seat.setSeatStatus(SeatStatus.RESERVED);
        seat.setReservedBy(userId);
        seat.setReservedAt(LocalDateTime.now());

        int eventUpdated = eventsRepository.decreaseIfAvailable(eventId);
        if (eventUpdated == 0) {
            // event가 매진이면 방금 잡은 seat을 되돌려야 함
            seatsRepository.rollbackReservedSeat(eventId, seatNumber, userId);
            throw new RuntimeException("sold out");
        }

        Events event = eventsRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("event not found"));


        Reservations r = new Reservations();
        r.setEvent(event);
        r.setSeats(seat);
        r.setStatus(ReservationStatus.PENDING);
        r.setCreatedAt(LocalDateTime.now());
        r.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        reservationsRepository.save(r);

        return r.getId();
    }


    @Transactional
    public UUID generateReservationNoLock(UUID eventId, String seatNumber, String userId) {

        Seats seat = seatsRepository.findByEvent_IdAndSeatNumberAndSeatStatus(eventId, seatNumber, SeatStatus.AVAILABLE)
                .orElseThrow(() -> new IllegalArgumentException("seat not found"));

        seat.setSeatStatus(SeatStatus.RESERVED);
        seat.setReservedBy(userId);
        seat.setReservedAt(LocalDateTime.now());

        int eventUpdated = eventsRepository.decreaseIfAvailable(eventId);
        if (eventUpdated == 0) {
            // event가 매진이면 방금 잡은 seat을 되돌려야 함
            seatsRepository.rollbackReservedSeat(eventId, seatNumber, userId);
            throw new RuntimeException("sold out");
        }

        Events event = eventsRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("event not found"));


        Reservations r = new Reservations();
        r.setEvent(event);
        r.setSeats(seat);
        r.setUserId(userId);
        r.setStatus(ReservationStatus.PENDING);
        r.setCreatedAt(LocalDateTime.now());
        r.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        reservationsRepository.save(r);

        return r.getId();
    }

    @Transactional
    public UUID generateReservationAtomicUpdate(UUID eventId, String seatNumber, String userId) {

        LocalDateTime now = LocalDateTime.now();

        int rowCount = seatsRepository.reserveIfAvailable(eventId, seatNumber, userId, now);

        if( rowCount == 0 ){

            throw new RuntimeException("sold out");
        }

        int eventUpdated = eventsRepository.decreaseIfAvailable(eventId);
        if (eventUpdated == 0) {
            // event가 매진이면 방금 잡은 seat을 되돌려야 함
            seatsRepository.rollbackReservedSeat(eventId, seatNumber, userId);
            throw new RuntimeException("sold out");
        }

        // 3) 내가 잡은 좌석을 userId로 확정 조회
        Seats seat = seatsRepository.findByEvent_IdAndSeatNumberAndReservedBy(eventId, seatNumber, userId)
                .orElseThrow(() -> new IllegalStateException("reserved seat not found"));

        Events event = eventsRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("event not found"));

        Reservations r = new Reservations();
        r.setEvent(event);
        r.setSeats(seat);
        r.setStatus(ReservationStatus.PENDING);
        r.setCreatedAt(now);
        r.setExpiresAt(now.plusMinutes(5));

        reservationsRepository.save(r);

        return r.getId();
    }

    @Transactional
    public UUID generateReservationOptimistic(UUID eventId, String seatNumber, String userId) {

        LocalDateTime now = LocalDateTime.now();

        Seats seat = seatsRepository.findByEvent_IdAndSeatNumberAndSeatStatus(eventId, seatNumber, SeatStatus.AVAILABLE)
                .orElseThrow(() -> new IllegalArgumentException("sold out"));

        seat.setSeatStatus(SeatStatus.RESERVED);
        seat.setReservedBy(userId);
        seat.setReservedAt(now);
        Seats afterSeat = seatsRepository.saveAndFlush(seat);

        int eventUpdated = eventsRepository.decreaseIfAvailable(eventId);
        if (eventUpdated == 0) {
            // event가 매진이면 방금 잡은 seat을 되돌려야 함
            seatsRepository.rollbackReservedSeat(eventId, seatNumber, userId);
            throw new RuntimeException("sold out");
        }

        Events event = eventsRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("event not found"));


        Reservations r = new Reservations();
        r.setEvent(event);
        r.setSeats(afterSeat);
        r.setStatus(ReservationStatus.PENDING);
        r.setCreatedAt(LocalDateTime.now());
        r.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        reservationsRepository.save(r);

        return r.getId();
    }
}
