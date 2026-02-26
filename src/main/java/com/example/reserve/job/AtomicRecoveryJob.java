package com.example.reserve.job;

import com.example.reserve.domain.ExpireRecoverySupport;
import com.example.reserve.entity.Reservations;
import com.example.reserve.model.enums.ReservationStatus;
import com.example.reserve.model.enums.SeatStatus;
import com.example.reserve.repository.ReservationsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AtomicRecoveryJob implements ExpireRecoveryJob {

    private final ReservationsRepository reservationsRepository;
    private final ExpireRecoverySupport support;

    @Override
    @Transactional
    public List<UUID>  runOnce(int batchSize, String workerId) {
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

//                    eventsRepository.increaseAvailableSeats( reservations.getEvent().getId(), 1);
//                    reservations.getEvent().setAvailableSeats(reservations.getEvent().getAvailableSeats() + 1);

                    return reservations;
                }).toList();

        List<Reservations> reservations = reservationsRepository.saveAll(afterReservations);

        return reservations.stream().map( r -> r.getId())
                .toList();
    }

    @jakarta.transaction.Transactional
    public List<UUID> runOnce() {

        Pageable pageable = PageRequest.of(
                0, 3,
                Sort.by(Sort.Direction.ASC,"createdAt")   // 정렬 기준 필드명
        );
        Page<Reservations> expiredReservations = reservationsRepository.findByStatusAndExpiresAtBefore(pageable, ReservationStatus.PENDING, LocalDateTime.now());

        List<Reservations> afterReservations = expiredReservations.stream()
                .map(reservations -> {

                    reservations.setStatus(ReservationStatus.EXPIRED);
                    reservations.getSeats().setSeatStatus(SeatStatus.AVAILABLE);
                    reservations.getSeats().setReservedBy(null);

//                    eventsRepository.increaseAvailableSeats( reservations.getEvent().getId(), 1);
//                    reservations.getEvent().setAvailableSeats(reservations.getEvent().getAvailableSeats() + 1);

                    return reservations;
                }).toList();

        List<Reservations> reservations = reservationsRepository.saveAll(afterReservations);

        return reservations.stream().map( r -> r.getId())
                .toList();
    }

//    private void sleepQuietly(long ms) {
//        if (ms <= 0) return;
//        try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
//    }
}