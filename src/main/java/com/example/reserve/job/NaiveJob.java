package com.example.reserve.job;


import com.example.reserve.entity.Reservations;
import com.example.reserve.entity.Seats;
import com.example.reserve.model.enums.ReservationStatus;
import com.example.reserve.model.enums.SeatStatus;
import com.example.reserve.repository.EventsRepository;
import com.example.reserve.repository.ReservationsRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
// 일반적으로 JOB을 만들어서 recovery 한다고 했을때
public class NaiveJob implements ExpireRecoveryJob{

    private final ReservationsRepository reservationsRepository;
    private final EventsRepository eventsRepository;
    public NaiveJob(ReservationsRepository reservationsRepository, EventsRepository eventsRepository) {
        this.reservationsRepository = reservationsRepository;
        this.eventsRepository = eventsRepository;
    }

    @Transactional
    @Override
    public List<UUID> runOnce(int batchSize, String workerId) {

        Pageable pageable = PageRequest.of(
                0, batchSize,
                Sort.by(Sort.Direction.ASC,"createdAt")   // 정렬 기준 필드명
        );
        Page<Reservations> expiredReservations = reservationsRepository.findByStatusAndExpiresAtBefore(pageable,ReservationStatus.PENDING, LocalDateTime.now());

        List<Reservations> afterReservations = expiredReservations.stream()
                .map(reservations -> {
                    reservations.setWorkerId(workerId);
                    reservations.setStatus(ReservationStatus.EXPIRED);
                    reservations.getSeats().setSeatStatus(SeatStatus.AVAILABLE);
                    reservations.getSeats().setReservedBy(null);

                    eventsRepository.increaseAvailableSeats( reservations.getEvent().getId(), 1);
//                    reservations.getEvent().setAvailableSeats(reservations.getEvent().getAvailableSeats() + 1);

                    return reservations;
                }).toList();

        List<Reservations> reservations = reservationsRepository.saveAll(afterReservations);

        return reservations.stream().map( r -> r.getId())
                .toList();
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
