package com.example.reserve.job;

import com.example.reserve.model.dto.ReservationRefs;
import com.example.reserve.repository.EventsRepository;
import com.example.reserve.repository.ReservationsRepository;
import com.example.reserve.repository.SeatsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class SelectForUpdateSkipJob implements ExpireRecoveryJob{

    private final ReservationsRepository reservationsRepository;
    private final EventsRepository eventsRepository;
    private final SeatsRepository seatsRepository;
    public SelectForUpdateSkipJob(ReservationsRepository reservationsRepository, EventsRepository eventsRepository, SeatsRepository seatsRepository) {
        this.reservationsRepository = reservationsRepository;
        this.eventsRepository = eventsRepository;
        this.seatsRepository = seatsRepository;
    }

    @Transactional( timeout = 5)
    @Override
    public List<UUID> runOnce(int batchSize, String workerId) {

        List<ReservationRefs> refs = reservationsRepository.findExpiredPendingIdsForUpdateSkip(batchSize);

        List<UUID> processed = new ArrayList<>();

//        try {
//            Thread.sleep(3000);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }

        for (ReservationRefs ref : refs) {

            try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
            // 1) reservation 상태 확정(조건부)
            int reservationUpdated = reservationsRepository.updateExpired(ref.getReservationId(), workerId);
            if (reservationUpdated == 0) {

                throw new RuntimeException("reservation was already processed.");// 이미 누가 처리했거나 결제됨
            }

            // 2) seat 복구(조건부)
            int seatRestored = seatsRepository.restoreSeatIfMatches(ref.getSeatId(), ref.getUserId());

            if(seatRestored == 0)
                throw new RuntimeException("seat was already processed.");
            // 이미 누가 처리했거나 결제됨

            // 3) seat이 실제로 복구된 경우만 event +1 (원자)
            if (seatRestored == 1) {
                eventsRepository.increaseAvailableSeats(ref.getEventId(), 1);
            }

            processed.add(ref.getReservationId());
        }

        return processed;
    }

    /**
     * ✅ 멀티 배치 버전
     * - reservation 상태 확정은 batch update (한 방)
     * - seat 복구는 안전하게 단건 loop(조건부)로 수행
     * - event +1은 seat 복구 성공건을 eventId로 집계해서 +N
     */
    @Transactional(timeout = 5)
    public List<UUID> runMulti(int batchSize, String workerId) {

        List<ReservationRefs> refs = reservationsRepository.findExpiredPendingIdsForUpdateSkip(batchSize);
        if (refs.isEmpty()) return List.of();

        // (실험용) 락 오래 잡기
//        try { Thread.sleep(3000); } catch (InterruptedException e) { throw new RuntimeException(e); }

        // 1) reservation ids 추출
        List<UUID> reservationIds = refs.stream()
                .map(ReservationRefs::getReservationId)
                .toList();

        // 2) reservation을 한 번에 EXPIRED 확정 (멱등)
        //    - 일부는 이미 처리되었을 수 있으니 "반드시 ids.size()와 같을 필요는 없음"
        reservationsRepository.updateExpiredBatch(reservationIds, workerId);

        // 3) seat 복구(조건부) + eventId별 복구 수 집계
        Map<UUID, Integer> eventRestoreCounts = new HashMap<>();
        List<UUID> processed = new ArrayList<>();

        for (ReservationRefs ref : refs) {

            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            int seatRestored = seatsRepository.restoreSeatIfMatches(ref.getSeatId(), ref.getUserId());
            if (seatRestored == 1) {
                eventRestoreCounts.merge(ref.getEventId(), 1, Integer::sum);
            }
            // reservation은 batch로 확정 시도했고, seat은 실패해도 멱등적으로 안전하므로
            // processed는 "이번 batch에서 대상으로 뽑은 예약" 기준으로 추가(원하면 seatRestored==1만 넣어도 됨)
            processed.add(ref.getReservationId());
        }

        // 4) event 재고 증가도 event별로 +N (원자)
        for (Map.Entry<UUID, Integer> e : eventRestoreCounts.entrySet()) {
            eventsRepository.increaseAvailableSeats(e.getKey(), e.getValue());
        }

        return processed;
    }

}