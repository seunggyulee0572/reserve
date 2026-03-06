package com.example.reserve.domain;

import com.example.reserve.entity.Events;
import com.example.reserve.entity.Reservations;
import com.example.reserve.entity.Seats;
import com.example.reserve.model.dto.ForPayment;
import com.example.reserve.model.dto.ReservationForPayment;
import com.example.reserve.model.dto.ReservationRefs;
import com.example.reserve.model.enums.ReservationStatus;
import com.example.reserve.model.enums.SeatStatus;
import com.example.reserve.repository.EventsRepository;
import com.example.reserve.repository.ReservationsRepository;
import com.example.reserve.repository.SeatsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
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
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));

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
        r.setCreatedAt(now);
        r.setExpiresAt(now.plusDays(1));
        r.setUserId(userId);
        r.setTotalAmount(seat.getBasePrice());

        reservationsRepository.save(r);

        return r.getId();
    }


    @Transactional
    public UUID generateReservationNoLock(UUID eventId, String seatNumber, String userId) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));

        Seats seat = seatsRepository.findByEvent_IdAndSeatNumberAndSeatStatus(eventId, seatNumber, SeatStatus.AVAILABLE)
                .orElseThrow(() -> new IllegalArgumentException("seat not found"));

        seat.setSeatStatus(SeatStatus.RESERVED);
        seat.setReservedBy(userId);
        seat.setReservedAt(LocalDateTime.now());

        seatsRepository.saveAndFlush(seat);
//        int eventUpdated = eventsRepository.decreaseIfAvailable(eventId);
//        if (eventUpdated == 0) {
//            // event가 매진이면 방금 잡은 seat을 되돌려야 함
//            seatsRepository.rollbackReservedSeat(eventId, seatNumber, userId);
//            throw new RuntimeException("sold out");
//        }

        eventsRepository.decreaseIfAvailable(eventId);
        Events event = eventsRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("event not found"));
//
//        event.setAvailableSeats(event.getAvailableSeats() - 1);
//
//        eventsRepository.saveAndFlush(event);

        Reservations r = new Reservations();
        r.setEvent(event);
        r.setSeats(seat);
        r.setUserId(userId);
        r.setStatus(ReservationStatus.PENDING);
        r.setCreatedAt(now);
        r.setExpiresAt(now.plusMinutes(5));
        r.setUpdatedAt(now);
        r.setTotalAmount(seat.getBasePrice());

        reservationsRepository.saveAndFlush(r);

        return r.getId();
    }

    @Transactional
    public UUID generateReservationAtomicUpdate(UUID eventId, String seatNumber, String userId) {

        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));

        int rowCount = seatsRepository.reserveIfAvailable(eventId, seatNumber, userId, now);

        if( rowCount == 0 ){

            throw new RuntimeException("sold out");
        }

        int eventUpdated = eventsRepository.decreaseIfAvailable(eventId);
        if (eventUpdated == 0) {
            // event가 매진이면 방금 잡은 seat을 되돌려야 함
            seatsRepository.rollbackReservedSeat(eventId, seatNumber, userId);
            throw new RuntimeException("no event seat");
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
        r.setUserId(userId);
        r.setTotalAmount(seat.getBasePrice());

        reservationsRepository.save(r);

        return r.getId();
    }


    @Transactional
    public UUID generateExpired(UUID eventId, String seatNumber, String userId) {

        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));

        int rowCount = seatsRepository.reserveIfAvailable(eventId, seatNumber, userId, now);

        if( rowCount == 0 ){

            throw new RuntimeException("sold out");
        }

        int eventUpdated = eventsRepository.decreaseIfAvailable(eventId);
        if (eventUpdated == 0) {
            // event가 매진이면 방금 잡은 seat을 되돌려야 함
            seatsRepository.rollbackReservedSeat(eventId, seatNumber, userId);
            throw new RuntimeException("no event seat");
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
        r.setExpiresAt(now.minusMinutes(4));
        r.setUserId(userId);
        r.setTotalAmount(seat.getBasePrice());

        reservationsRepository.save(r);

        return r.getId();
    }

    @Transactional
    public UUID generateReservationOptimistic(UUID eventId, String seatNumber, String userId) {

        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));

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
        r.setCreatedAt(now);
        r.setExpiresAt(now.plusMinutes(5));
        r.setUserId(userId);
        r.setTotalAmount(seat.getBasePrice());

        reservationsRepository.save(r);

        return r.getId();
    }

    @Transactional
    public ForPayment getForPayment(String seatNumber) {

        Reservations reservations = reservationsRepository.findReservationsBySeats_SeatNumberAndStatus(seatNumber, ReservationStatus.PENDING)
                .orElseThrow(() -> new IllegalArgumentException("no reservation for payment"));


        return new ForPayment( reservations.getId(), reservations.getSeats().getId(), reservations.getUserId() ,reservations.getTotalAmount());

    }

    @Transactional
    public List<ReservationForPayment> getReservedSeat(UUID eventId, int limit ) {
        return reservationsRepository.findActiveReservationsNative(
                eventId,
                ReservationStatus.PENDING.name(),
                limit);


    }

    @Transactional
    public void releaseSeatsByReservationId( UUID reservationId ){

        ReservationRefs ref = reservationsRepository.findRefsById(reservationId);

        Reservations reservations = reservationsRepository.findById(reservationId).orElseThrow();

        reservations.setStatus(ReservationStatus.CANCELLED);

        int update = seatsRepository.restoreSeatIfMatches(ref.getSeatId(), ref.getUserId());

        if( update == 0)
            throw new IllegalArgumentException("좌석 되돌리기 실패");

        int eventUpdate = eventsRepository.increaseAvailableSeats(ref.getEventId(), 1);

        if( eventUpdate == 0)
            throw new IllegalArgumentException("좌석 수 되돌리기 실패");
    }


}
