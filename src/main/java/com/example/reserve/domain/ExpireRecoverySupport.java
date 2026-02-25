package com.example.reserve.domain;

import com.example.reserve.model.dto.ReservationRefs;
import com.example.reserve.repository.EventsRepository;
import com.example.reserve.repository.ReservationsRepository;
import com.example.reserve.repository.SeatsRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ExpireRecoverySupport {

    private final ReservationsRepository reservationsRepository;
    private final SeatsRepository seatsRepository;
    private final EventsRepository eventsRepository;

    /**
     * 1건 만료 복구:
     * - reservation을 EXPIRED로 확정(조건부)
     * - seat을 AVAILABLE로 복구(조건부)
     * - seat 복구 성공한 경우에만 event.available_seats +1
     */
    @Transactional
    public boolean expireOne(UUID reservationId ) {

        // 예약 참조 가져오기 (event_id, seat_id, user_id)
        ReservationRefs refs = reservationsRepository.findRefsById(reservationId);
        if (refs == null) return false;

        // 1) reservation 상태를 EXPIRED로 확정 (결제 레이스 방지 옵션)
        int expiredUpdated = reservationsRepository.markExpired(reservationId);

        if (expiredUpdated == 0) {
            // 이미 CONFIRMED/CANCELLED 등으로 바뀌었거나, 누가 먼저 처리함
            return false;
        }

        // 2) seat 복구 (조건부)
        int seatRestored = seatsRepository.restoreSeatIfMatches( refs.getSeatId(), refs.getUserId() );

        // 3) seat이 실제로 복구된 경우에만 event 재고 +1
        if (seatRestored == 1) {
            eventsRepository.increaseAvailableSeats(refs.getEventId(), 1);
        }

        return true;
    }


    @Transactional
    public boolean expireStrictOne(UUID reservationId ) {

        // 예약 참조 가져오기 (event_id, seat_id, user_id)
        ReservationRefs refs = reservationsRepository.findRefsById(reservationId);
        if (refs == null) return false;

        // 1) reservation 상태를 EXPIRED로 확정 (결제 레이스 방지 옵션)
        int expiredUpdated = reservationsRepository.markExpiredIfStillExpired(reservationId);

        if (expiredUpdated == 0) {
            // 이미 CONFIRMED/CANCELLED 등으로 바뀌었거나, 누가 먼저 처리함
            return false;
        }

        // 2) seat 복구 (조건부)
        int seatRestored = seatsRepository.restoreSeatIfMatches( refs.getSeatId(), refs.getUserId() );

        // 3) seat이 실제로 복구된 경우에만 event 재고 +1
        if (seatRestored == 1) {
            eventsRepository.increaseAvailableSeats(refs.getEventId(), 1);
        }

        return true;
    }

}