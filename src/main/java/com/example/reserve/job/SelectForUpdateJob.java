package com.example.reserve.job;

import com.example.reserve.entity.Reservations;
import com.example.reserve.model.dto.ReservationRefs;
import com.example.reserve.model.enums.ReservationStatus;
import com.example.reserve.model.enums.SeatStatus;
import com.example.reserve.repository.EventsRepository;
import com.example.reserve.repository.ReservationsRepository;
import com.example.reserve.repository.SeatsRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SelectForUpdateJob implements ExpireRecoveryJob{

    private final ReservationsRepository reservationsRepository;
    private final EventsRepository eventsRepository;
    private final SeatsRepository seatsRepository;
    public SelectForUpdateJob(ReservationsRepository reservationsRepository, EventsRepository eventsRepository, SeatsRepository seatsRepository) {
        this.reservationsRepository = reservationsRepository;
        this.eventsRepository = eventsRepository;
        this.seatsRepository = seatsRepository;
    }

    @Transactional(timeout = 5)
    @Override
    public List<UUID> runOnce(int batchSize, String workerId) {

        List<ReservationRefs> refs = reservationsRepository.findExpiredPendingIdsForUpdate(batchSize);

        List<UUID> processed = new ArrayList<>();

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        for (ReservationRefs ref : refs) {
            UUID res = ref.getReservationId();
            String user = ref.getUserId();
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

//    @Transactional
//    @Override
//    public List<UUID> runMulti(int batchSize, String workerId) {
//
//
//        reservationsRepository.findExpiredPendingRefs(batchSize,ReservationStatus.PENDING, LocalDateTime.now());
//
//        List<UUID> reservationIds = expiredReservations.stream().map(Reservations::getId).toList();
//        List<UUID> seatIds = expiredReservations.stream().map( r -> r.getSeats().).toList();
//        List<UUID> eventIds = expiredReservations.stream().map(Reservations::getId).toList();
//
//        return List.of();
//    }


}
