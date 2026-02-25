package com.example.reserve.job;


import com.example.reserve.entity.Reservations;
import com.example.reserve.entity.Seats;
import com.example.reserve.model.enums.ReservationStatus;
import com.example.reserve.model.enums.SeatStatus;
import com.example.reserve.repository.ReservationsRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
// 일반적으로 JOB을 만들어서 recovery 한다고 했을때
public class TestJob implements ExpireRecoveryJob{

    private final ReservationsRepository reservationsRepository;

    public TestJob(ReservationsRepository reservationsRepository) {
        this.reservationsRepository = reservationsRepository;
    }

    @Override
    public int runOnce(int batchSize, String workerId) {

        List<Reservations> expiredReservations = reservationsRepository.findByStatusAndExpiresAtBefore(ReservationStatus.PENDING, LocalDateTime.now());

        List<Reservations> afterReservations = expiredReservations.stream()
                .limit(3)
                .map(reservations -> {

                    reservations.setStatus(ReservationStatus.EXPIRED);
                    reservations.getSeats().setSeatStatus(SeatStatus.AVAILABLE);
                    reservations.getEvent().setAvailableSeats(reservations.getEvent().getAvailableSeats());

                    return reservations;
                }).toList();

        List<Reservations> reservations = reservationsRepository.saveAll(afterReservations);

        return reservations.size();
    }

    @Transactional
    public List<UUID> runOnce() {

        List<Reservations> expiredReservations = reservationsRepository.findByStatusAndExpiresAtBefore(ReservationStatus.PENDING, LocalDateTime.now());

        List<Reservations> afterReservations = expiredReservations.stream()
                .limit(3)
                .map(reservations -> {

                    reservations.setStatus(ReservationStatus.EXPIRED);
                    reservations.getSeats().setSeatStatus(SeatStatus.AVAILABLE);
                    reservations.getEvent().setAvailableSeats(reservations.getEvent().getAvailableSeats());

                    return reservations;
                }).toList();

        List<Reservations> reservations = reservationsRepository.saveAll(afterReservations);

        return reservations.stream().map( r -> r.getId())
                .toList();
    }
}
