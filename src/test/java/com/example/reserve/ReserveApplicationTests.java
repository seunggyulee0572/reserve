package com.example.reserve;

import com.example.reserve.domain.EventService;
import com.example.reserve.domain.ReservationService;
import com.example.reserve.domain.SeatService;
import com.example.reserve.entity.Events;
import com.example.reserve.entity.Reservations;
import com.example.reserve.entity.Seats;
import com.example.reserve.job.NaiveJob;
import com.example.reserve.model.AttemptResult;
import com.example.reserve.repository.EventsRepository;
import com.example.reserve.repository.ReservationsRepository;
import com.example.reserve.repository.SeatsRepository;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
    EventService eventService;
    @Autowired
    SeatService seatService;
    @Autowired
    ReservationsRepository reservationsRepository;
    @Autowired
    NaiveJob naiveJob;
    @Autowired
    JdbcTemplate jdbcTemplate;


    private final UUID eventId = UUID.fromString("628032ff-77fa-49a4-a9b9-0f588ac94f32");
    @Test
    void race_pessimistic_lock() throws Exception {


        String seatNumber = "19-A";

        int threads = 500;
        long t0 = System.nanoTime();
        var before = innodbRowLockSnapshot();

        var results = runRace(threads, () -> {
            String userId = "u-" + Thread.currentThread().getId();
            return reservationService.generateReservation(eventId, seatNumber, userId);
        });

        var after = innodbRowLockSnapshot();
        printDiff("pessimistic_lock", before, after);

        long tookMs = (System.nanoTime() - t0) / 1_000_000;
        System.out.println( "pessimistic_lock , total : " + tookMs);
        printSummary("pessimistic_lock", results);
        verifyDbState(eventId, seatNumber);
    }

    @Test
    void race_no_lock() throws Exception {
        String seatNumber = "31-A";
        int threads = 500;

        long t0 = System.nanoTime();
        var before = innodbRowLockSnapshot();

        var results = runRace(threads, () -> {
            String userId = "u-" + Thread.currentThread().getId();
            return reservationService.generateReservationNoLock(eventId, seatNumber, userId);
        });

        var after = innodbRowLockSnapshot();
        printDiff("no_lock", before, after);

        long tookMs = (System.nanoTime() - t0) / 1_000_000;

        System.out.println( "no_lock , total : " + tookMs);
        printSummary("no_lock", results);
        verifyDbState(eventId, seatNumber);

    }

    @Test
    void race_atomic_update() throws Exception {
        String seatNumber = "32-A";
        int threads = 500;
        long t0 = System.nanoTime();
        var before = innodbRowLockSnapshot();

        var results = runRace(threads, () -> {
            String userId = "u-" + Thread.currentThread().getId();
            return reservationService.generateReservationAtomicUpdate(eventId, seatNumber, userId);
        });

        var after = innodbRowLockSnapshot();
        printDiff("atomic_update", before, after);

        long tookMs = (System.nanoTime() - t0) / 1_000_000;

        System.out.println( "atomic_update , total : " + tookMs);
        printSummary("atomic_update", results);
        verifyDbState(eventId, seatNumber);
    }

    @Test
    void race_optimistic() throws Exception {
        String seatNumber = "33-A";
        int threads = 500;
        long t0 = System.nanoTime();

        var before = innodbRowLockSnapshot();

        var results = runRace(threads, () -> {
            String userId = "u-" + Thread.currentThread().getId();
            return reservationService.generateReservationOptimistic(eventId, seatNumber, userId);
        });

        var after = innodbRowLockSnapshot();
        printDiff("optimistic", before, after);

        long tookMs = (System.nanoTime() - t0) / 1_000_000;

        System.out.println( "optimistic , total : " + tookMs);
        printSummary("optimistic", results);
        verifyDbState(eventId, seatNumber);
    }

    // ========= schedule job ================

    @Test
    void naive_schedule() throws Exception {
        int threads = 5;
        long t0 = System.nanoTime();
        var before = innodbRowLockSnapshot();

        var results = runScheduleRace(threads,
                (idx, workerId) -> naiveJob.runOnce(3, workerId)
        );
        var after = innodbRowLockSnapshot();
        printDiff("naive", before, after);

        long tookMs = (System.nanoTime() - t0) / 1_000_000;

        System.out.println( "naive_schedule , total : " + tookMs);
        printSummary("naive_schedule", results);

        List<UUID> ids =
                results.stream()
                        .filter(AttemptResult::success)
                        .flatMap(res -> Arrays.stream(res.reservationId().split(","))) // Stream<String>
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(UUID::fromString)   // 여기서 UUID 파싱
                        .toList();

        verifyScheduleState( ids );
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

        Events event = eventsRepository.findById(eventId).orElseThrow();

        long availableCount = event.getAvailableSeats();
        long reservationCnt = reservationsRepository.countByEvent_IdAndSeats_Id(eventId, seat.getId());

        System.out.println("[DB] seatStatus=" + seat.getSeatStatus()
                + " reservedBy=" + seat.getReservedBy()
                + " reservationCnt=" + reservationCnt
                + " availableCount=" + availableCount);

        // 기대: 한 좌석이면 reservationCnt는 보통 1이 나와야 정상
    }

    private void verifyScheduleState( List<UUID> ids) {

        List<Reservations> reservations = reservationsRepository.findsByReservationsIds(ids);

        reservations.stream().forEach( r -> {

            Seats seat = r.getSeats();
            Events event = r.getEvent();
            System.out.println("[DB] seatStatus=" + seat.getSeatStatus()
                    + " reservationId=" + r.getId()
                    + " reservedBy=" + seat.getReservedBy()
                    + " reservationStatus=" + r.getStatus()
                    + " availableCount=" + event.getAvailableSeats());

        } );

    }

    @Test
    @Transactional
    @Rollback(false)
    void contextLoads() {

        eventService.generateEvent();
    }

    @Test
    @Transactional
    @Rollback(false)
    void initSeats() {

        UUID eventId = eventService.getEventId();
        System.out.println(eventId);
        seatService.makeSeat( eventId );
    }

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


    private List<AttemptResult> runScheduleRace(
            int threads,
            java.util.function.BiFunction<Integer, String, List<UUID>> action
    ) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(threads);

        List<AttemptResult> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            final String workerId = "w-" + idx;

            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    long t0 = System.nanoTime();

                    List<UUID> reservationIds = action.apply(idx, workerId);

                    long tookMs = (System.nanoTime() - t0) / 1_000_000;
                    results.add(new AttemptResult(
                            idx, true,
                            String.join(",", reservationIds.stream().map(UUID::toString).toList()),
                            null, null, tookMs
                    ));
                } catch (Exception e) {
                    results.add(new AttemptResult(
                            idx, false, null,
                            e.getClass().getSimpleName(),
                            e.getMessage(),
                            0
                    ));
                } finally {
                    done.countDown();
                }
                return null;
            });
        }

        ready.await();
        start.countDown();
        done.await();
        pool.shutdown();
        return results;
    }


    private Map<String, Long> innodbRowLockSnapshot() {
        return jdbcTemplate.query(
                "SHOW GLOBAL STATUS LIKE 'Innodb_row_lock%';",
                rs -> {
                    Map<String, Long> m = new LinkedHashMap<>();
                    while (rs.next()) {
                        String name = rs.getString("Variable_name");
                        String value = rs.getString("Value");
                        // 숫자 아닌 값이 올 가능성 거의 없지만 방어
                        long v = 0L;
                        try { v = Long.parseLong(value); } catch (Exception ignored) {}
                        m.put(name, v);
                    }
                    return m;
                }
        );
    }

    private void printDiff(String title, Map<String, Long> before, Map<String, Long> after) {
        System.out.println("\n=== " + title + " : InnoDB row lock status diff ===");
        for (var e : after.entrySet()) {
            long b = before.getOrDefault(e.getKey(), 0L);
            long a = e.getValue();
            System.out.printf("%s: before=%d after=%d diff=%d%n", e.getKey(), b, a, (a - b));
        }
    }


}
