package com.example.reserve;

import com.example.reserve.domain.EventService;
import com.example.reserve.domain.ReservationService;
import com.example.reserve.domain.SeatService;
import com.example.reserve.model.AttemptResult;
import com.example.reserve.repository.EventsRepository;
import com.example.reserve.repository.ReservationsRepository;
import com.example.reserve.repository.SeatsRepository;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@SpringBootTest
class ReserveApplicationTests {

    @Autowired
    ReservationService reservationService;
    @Autowired
    SeatsRepository seatsRepository;
    @Autowired
    EventsRepository eventsRepository;
    @Autowired
    ReservationsRepository reservationsRepository;

    private final UUID eventId = UUID.fromString("2c5e47a4-057a-471f-a6e3-d5b998bc45b5");
    @Test
    void race_pessimistic_lock() throws Exception {

        String seatNumber = "1-A";

        int threads = 50;

        var results = runRace(threads, () -> {
            String userId = "u-" + Thread.currentThread().getId();
            return reservationService.generateReservation(eventId, seatNumber, userId);
        });

        printSummary("pessimistic_lock", results);
        verifyDbState(eventId, seatNumber);
    }

    @Test
    void race_no_lock() throws Exception {
        String seatNumber = "1-A";
        int threads = 500;

        var results = runRace(threads, () -> {
            String userId = "u-" + Thread.currentThread().getId();
            return reservationService.generateReservationNoLock(eventId, seatNumber, userId);
        });

        printSummary("no_lock", results);
        verifyDbState(eventId, seatNumber);

        Thread.sleep(4000000);

    }

    @Test
    void race_atomic_update() throws Exception {
        String seatNumber = "1-A";
        int threads = 50;

        var results = runRace(threads, () -> {
            String userId = "u-" + Thread.currentThread().getId();
            return reservationService.generateReservationAtomicUpdate(eventId, seatNumber, userId);
        });

        printSummary("atomic_update", results);
        verifyDbState(eventId, seatNumber);
    }

    @Test
    void race_optimistic() throws Exception {
        String seatNumber = "1-A";
        int threads = 50;

        var results = runRace(threads, () -> {
            String userId = "u-" + Thread.currentThread().getId();
            return reservationService.generateReservationOptimistic(eventId, seatNumber, userId);
        });

        printSummary("optimistic", results);
        verifyDbState(eventId, seatNumber);
    }

    // ===== 결과 출력 =====

    private void printSummary(String label, List<AttemptResult> results) {
        long ok = results.stream().filter(AttemptResult::success).count();
        long fail = results.size() - ok;

        var top = results.stream()
                .filter(AttemptResult::success)
                .sorted(Comparator.comparingLong(AttemptResult::tookMs))
                .limit(5)
                .toList();

        var errors = results.stream()
                .filter(r -> !r.success())
                .collect(Collectors.groupingBy(
                        r -> r.errorType() + ":" + r.errorMsg(),
                        Collectors.counting()
                ));

        System.out.println("==== " + label + " ====");
        System.out.println("threads=" + results.size() + " success=" + ok + " fail=" + fail);
        System.out.println("fail reasons=" + errors);
        System.out.println("fastest success=" + top);
    }

    // ===== DB 정합성 검증 =====
    private void verifyDbState(UUID eventId, String seatNumber) {
        var seat = seatsRepository.findByEvent_IdAndSeatNumber(eventId, seatNumber)
                .orElseThrow();

        long reservationCnt = reservationsRepository.countByEvent_IdAndSeats_Id(eventId, seat.getId());

        System.out.println("[DB] seatStatus=" + seat.getSeatStatus()
                + " reservedBy=" + seat.getReservedBy()
                + " reservationCnt=" + reservationCnt);

        // 기대: 한 좌석이면 reservationCnt는 보통 1이 나와야 정상
    }

//    @Test
//    @Transactional
//    @Rollback(false)
//    void contextLoads() {
//
//        eventService.generateEvent();
//    }
//
//    @Test
//    @Transactional
//    @Rollback(false)
//    void initSeats() {
//
//        UUID eventId = eventService.getEventId();
//        seatService.makeSeat( eventId );
//    }

    private List<AttemptResult> runRace(int threads, Callable<UUID> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(threads);

        List<AttemptResult> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    long t0 = System.nanoTime();
                    UUID rid = action.call();
                    long tookMs = (System.nanoTime() - t0) / 1_000_000;
                    results.add(new AttemptResult(idx, true, rid.toString(), null, null, tookMs));
                } catch (Exception e) {
                    long tookMs = 0;
                    results.add(new AttemptResult(
                            idx,
                            false,
                            null,
                            e.getClass().getSimpleName(),
                            e.getMessage(),
                            tookMs
                    ));
                } finally {
                    done.countDown();
                }
                return null;
            });
        }

        // 모든 스레드 준비될 때까지 대기 후 동시 발사
        ready.await();
        start.countDown();
        done.await();

        pool.shutdown();
        return results;
    }


}
