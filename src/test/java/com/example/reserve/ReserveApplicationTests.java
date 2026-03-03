package com.example.reserve;

import com.example.reserve.domain.EventService;
import com.example.reserve.domain.PaymentService;
import com.example.reserve.domain.ReservationService;
import com.example.reserve.domain.SeatService;
import com.example.reserve.entity.Events;
import com.example.reserve.entity.Payments;
import com.example.reserve.entity.Reservations;
import com.example.reserve.entity.Seats;
import com.example.reserve.job.NaiveJob;
import com.example.reserve.job.SelectForUpdateJob;
import com.example.reserve.job.SelectForUpdateSkipJob;
import com.example.reserve.job.UpdateFirstJob;
import com.example.reserve.model.AttemptResult;
import com.example.reserve.model.dto.ForPayment;
import com.example.reserve.model.dto.ReservationForPayment;
import com.example.reserve.model.enums.PaymentsStatus;
import com.example.reserve.model.enums.ReservationStatus;
import com.example.reserve.model.enums.SeatStatus;
import com.example.reserve.repository.EventsRepository;
import com.example.reserve.repository.ReservationsRepository;
import com.example.reserve.repository.SeatsRepository;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    PaymentService paymentService;
    @Autowired
    NaiveJob naiveJob;
    @Autowired
    SelectForUpdateJob selectForUpdateJob;
    @Autowired
    SelectForUpdateSkipJob selectForUpdateSkipJob;
    @Autowired
    UpdateFirstJob updateFirstJob;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void setup() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    private List<String> generateSeatNumbers() {
        List<String> seats = new ArrayList<>();

        for (int row = 1; row <= 20; row++) {
            for (char col = 'A'; col <= 'F'; col++) {
                seats.add(row + "-" + col);
            }
        }

        return seats;
    }

    private final UUID eventId = UUID.fromString("628032ff-77fa-49a4-a9b9-0f588ac94f32");

    @Test
    @Transactional
    @Rollback(false)
    void initSeats() {

        seatService.makeSeat( eventId );
    }

    @Test
    void generate_expired_seat() throws Exception {

        List<String> seats = new ArrayList<>();

        for (int row = 21; row <= 40; row++) {
            for (char col = 'A'; col <= 'F'; col++) {
                seats.add(row + "-" + col);
            }
        }

        for (String seatNumber : seats) {

            int threads = 500;
            long t0 = System.nanoTime();
            var before = innodbRowLockSnapshot();

            var results = runRace(threads, () -> {
                String userId = "u-" + Thread.currentThread().getId();
                return reservationService.generateExpired(eventId, seatNumber, userId);
            });

//            var after = innodbRowLockSnapshot();
//            printDiff("pessimistic_lock_" + seatNumber, before, after);
//
//            long tookMs = (System.nanoTime() - t0) / 1_000_000;
//            System.out.println("seat=" + seatNumber + " total=" + tookMs);
//
//            printSummary("pessimistic_lock_" + seatNumber, results);
//            verifyDbState(eventId, seatNumber);
        }
    }

    @Test
    void race_pessimistic_lock() throws Exception {


//        String seatNumber = "1-A";
        List<String> seats = generateSeatNumbers();

        for (String seatNumber : seats) {

            int threads = 500;
            long t0 = System.nanoTime();
            var before = innodbRowLockSnapshot();

            var results = runRace(threads, () -> {
                String userId = "u-" + Thread.currentThread().getId();
                return reservationService.generateReservation(eventId, seatNumber, userId);
            });

            var after = innodbRowLockSnapshot();
            printDiff("pessimistic_lock_" + seatNumber, before, after);

            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            System.out.println("seat=" + seatNumber + " total=" + tookMs);

            printSummary("pessimistic_lock_" + seatNumber, results);
            verifyDbState(eventId, seatNumber);
        }
    }

    // 이건 가장 위험, lost update로 seat은 한개만 잡거나 event available 도 같게 수정되었을지 몰라도
    // reservation이 success 만큼 생성, seat 에서 잡은 user 와 reservation 의 user 차이 발생
    @Test
    void race_no_lock() throws Exception {
        String seatNumber = "2-A";
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
        String seatNumber = "3-A";
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

    // version 필수
    // version 주석 후 테스트 결과 no lock 이랑 동일하게 동작
    @Test
    void race_optimistic() throws Exception {
        String seatNumber = "5-A";
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

    // 여기서 version 을 사용하나 안하냐에 따라서 갈림
    // version을 사용 안한경우 동시에 여러개의 스레드가 접근해서 수정
    // 근데 결과를 보면 여러개의 스레드가 동시에 접근해서 수정한것 같지 않은것도 있는데 그건 lost update 때문
    // 원자적 update로 수정해서 보면 available seat의 개수가 접근 한 횟수만큼 늘어나있음
    // version을 사용한 결과 모든 스레드가 접근하지만 실질적으로 update가 가능한건 1개뿐이고 나머지는 fail 발생
    // 그래서 하나의 스레드만 동작해서 안전하게 처리
    // 하지만 스케줄러 처리 같은 경우에서는 성능이 떨어짐, 하나가 실피하면 다른 스레드들은 다른 스레드가 선점하지 않은
    // 작업을 잡아야 되는데 lock이 된 걸 같이 잡아서 처리 못함
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

    // 동시에 접근 할 경우
    @Test
    void select_for_update_schedule() throws Exception {
        int threads = 5;
        long t0 = System.nanoTime();
        var before = innodbRowLockSnapshot();

        var results = runScheduleRace(threads,
                (idx, workerId) -> selectForUpdateJob.runOnce(3, workerId)
        );
        var after = innodbRowLockSnapshot();
        printDiff("select_for_update", before, after);

        long tookMs = (System.nanoTime() - t0) / 1_000_000;

        System.out.println( "select_for_update_schedule , total : " + tookMs);
        printSummary("select_for_update_schedule", results);

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

    @Test
    void select_for_update_skip_schedule() throws Exception {
        int threads = 5;
        long t0 = System.nanoTime();
        var before = innodbRowLockSnapshot();

        var results = runScheduleRace(threads,
                (idx, workerId) -> selectForUpdateSkipJob.runOnce(20, workerId)
        );
        var after = innodbRowLockSnapshot();
        printDiff("select_for_update_skip", before, after);

        long tookMs = (System.nanoTime() - t0) / 1_000_000;

        System.out.println( "select_for_update_skip_schedule , total : " + tookMs);
        printSummary("select_for_update_skip_schedule", results);

        List<UUID> ids =
                results.stream()
                        .filter(AttemptResult::success)
                        .flatMap(res -> Arrays.stream(res.reservationId().split(","))) // Stream<String>
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(UUID::fromString)   // 여기서 UUID 파싱
                        .toList();

        verifyScheduleState( ids );

        printScheduleMetrics("skip_once", tookMs, results);
    }

    @Test
    void select_for_update_skip_batch_schedule() throws Exception {
        int threads = 5;
        long t0 = System.nanoTime();
        var before = innodbRowLockSnapshot();

        var results = runScheduleRace(threads,
                (idx, workerId) -> selectForUpdateSkipJob.runMulti(40, workerId)
        );
        var after = innodbRowLockSnapshot();
        printDiff("select_for_update_skip_batch", before, after);

        long tookMs = (System.nanoTime() - t0) / 1_000_000;

        System.out.println( "select_for_update_skip_batch_schedule , total : " + tookMs);
        printSummary("select_for_update_skip_batch_schedule", results);

        List<UUID> ids =
                results.stream()
                        .filter(AttemptResult::success)
                        .flatMap(res -> Arrays.stream(res.reservationId().split(","))) // Stream<String>
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(UUID::fromString)   // 여기서 UUID 파싱
                        .toList();

        verifyScheduleState( ids );

        printScheduleMetrics("skip_batch", tookMs, results);
    }

    @Test
    void claim_update_schedule() throws Exception {
        int threads = 5;
        long t0 = System.nanoTime();
        var before = innodbRowLockSnapshot();

        var results = runScheduleRace(threads,
                (idx, workerId) -> updateFirstJob.runOnce(3, workerId)
        );
        var after = innodbRowLockSnapshot();
        printDiff("claim_update", before, after);

        long tookMs = (System.nanoTime() - t0) / 1_000_000;

        System.out.println( "claim_update_schedule , total : " + tookMs);
        printSummary("claim_update_schedule", results);

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

    // ============== 결제 테스트 ================

    @Test
    void concurrent_payments_each_thread_has_own_reservation() throws Exception {
        int users = 20;
        List<TestCase> cases = new ArrayList<>();

        List<ReservationForPayment> reservedSeat = reservationService.getReservedSeat(eventId, users);

        for (ReservationForPayment res : reservedSeat) {

            String idemKey = "idem-" + res.getUserId() + "-" + UUID.randomUUID();
            cases.add(new TestCase(res.getUserId(), res.getReservationId(), res.getTotalAmount(), idemKey));

        }

        ExecutorService pool = Executors.newFixedThreadPool(users);

        // 1) requestPayment 동시 실행
        runConcurrent(pool, users, idx -> {
            TestCase tc = cases.get(idx);
            try {
                paymentService.requestPayment(tc.reservationId(), tc.amount(), tc.idemKey());
            } catch (Exception e) {
                System.out.println("requestPayment 실패 idx=" + idx + " : " + e.getMessage());
            }
        });

        // 2) PG 처리 시간 가정
        Thread.sleep(300);

        // 3) processPayment 동시 실행 (성공/실패 섞기)
        runConcurrent(pool, users, idx -> {
            TestCase tc = cases.get(idx);

            boolean success = (idx % 5 != 0); // 80% 성공 예시
            PaymentsStatus status = success ? PaymentsStatus.SUCCESS : PaymentsStatus.FAILED;
            BigDecimal pgAmount = success ? tc.amount() : tc.amount().add(BigDecimal.ONE);

            try {
                paymentService.processPayment(
                        "pg-" + UUID.randomUUID(),
                        tc.idemKey(),
                        pgAmount,
                        status
                );
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        });

        pool.shutdown();

        // 4) 검증: 각 reservation 상태/seat 상태/결제 상태 확인
        long successCnt = 0;
        long failedCnt = 0;

        for (TestCase tc : cases) {
            // 서비스에서 한 번에 묶어서 조회해온 데이터를 받음
            PaymentService.PaymentDetailView detail = paymentService.getPaymentDetailForVerification(tc.idemKey(), tc.reservationId());

            if (detail.paymentStatus() == PaymentsStatus.SUCCESS) {
                successCnt++;
                // 성공이면 확정 상태여야 함
                assertEquals(ReservationStatus.CONFIRMED, detail.reservationStatus());
                assertEquals(SeatStatus.CONFIRMED, detail.seatStatus());
            } else {
                failedCnt++;
                // 실패 시 로직 검증 (예: PENDING 유지 등)
                // assertEquals(ReservationStatus.PENDING, detail.reservationStatus());
            }
        }

        System.out.println("payment success=" + successCnt + ", failed=" + failedCnt);
    }

    private void runConcurrent(ExecutorService pool, int tasks, ThrowingIntConsumer action) throws Exception {
        CountDownLatch ready = new CountDownLatch(tasks);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(tasks);

        for (int i = 0; i < tasks; i++) {
            final int idx = i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    action.accept(idx);
                } finally {
                    done.countDown();
                }
                return null;
            });
        }

        ready.await();
        start.countDown();
        done.await();
    }

    @Test
    void payment_and_scheduler_lock_contention() throws Exception {

        int paymentUsers = 20;
        int schedulerThreads = 5;

        // 결제용 예약 데이터 준비
        List<TestCase> cases = new ArrayList<>();
        List<ReservationForPayment> reservedSeat = reservationService.getReservedSeat(eventId, paymentUsers);
        for (ReservationForPayment res : reservedSeat) {
            String idemKey = "idem-" + res.getUserId() + "-" + UUID.randomUUID();
            cases.add(new TestCase(res.getUserId(), res.getReservationId(), res.getTotalAmount(), idemKey));
        }

        ExecutorService paymentPool = Executors.newFixedThreadPool(paymentUsers);
        ExecutorService schedulerPool = Executors.newFixedThreadPool(schedulerThreads);

        CountDownLatch ready = new CountDownLatch(paymentUsers + schedulerThreads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(paymentUsers + schedulerThreads);

        List<Long> paymentLatencies = new CopyOnWriteArrayList<>();
        List<String> timeoutErrors = new CopyOnWriteArrayList<>();

        // 결제 스레드 준비
        for (int i = 0; i < paymentUsers; i++) {
            final int idx = i;
            paymentPool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    long t0 = System.nanoTime();

                    // requestPayment
                    try {
                        paymentService.requestPayment(
                                cases.get(idx).reservationId(),
                                cases.get(idx).amount(),
                                cases.get(idx).idemKey()
                        );
                    } catch (Exception e) {
                        System.out.println("requestPayment 실패 idx=" + idx + " : " + e.getMessage());
                    }

                    Thread.sleep(300); // PG 처리 시간 가정

                    // processPayment
                    boolean success = (idx % 5 != 0);
                    PaymentsStatus status = success ? PaymentsStatus.SUCCESS : PaymentsStatus.FAILED;
                    BigDecimal pgAmount = success
                            ? cases.get(idx).amount()
                            : cases.get(idx).amount().add(BigDecimal.ONE);

                    try {
                        paymentService.processPayment(
                                "pg-" + UUID.randomUUID(),
                                cases.get(idx).idemKey(),
                                pgAmount,
                                status
                        );
                        long latency = (System.nanoTime() - t0) / 1_000_000;
                        paymentLatencies.add(latency);
                        System.out.printf("[PAYMENT] idx=%d latency=%dms%n", idx, latency);
                    } catch (Exception e) {
                        timeoutErrors.add("payment-" + idx + ": " + e.getMessage());
                        System.out.println("[PAYMENT ERROR] idx=" + idx + " : " + e.getMessage());
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
                return null;
            });
        }

        // 스케줄러 스레드 준비 (락을 오래 잡도록 의도)
        var before = innodbRowLockSnapshot();
        List<AttemptResult> scheduleResults = new CopyOnWriteArrayList<>();

        for (int i = 0; i < schedulerThreads; i++) {
            final int idx = i;
            final String workerId = "w-" + idx;
            schedulerPool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    long st0 = System.nanoTime();

                    List<UUID> reservationIds = selectForUpdateJob.runOnce(3, workerId);

                    long tookMs = (System.nanoTime() - st0) / 1_000_000;
                    scheduleResults.add(new AttemptResult(
                            idx, true,
                            String.join(",", reservationIds.stream().map(UUID::toString).toList()),
                            null, null, tookMs
                    ));
                    System.out.printf("[SCHEDULER] worker=%s latency=%dms%n", workerId, tookMs);

                } catch (Exception e) {
                    scheduleResults.add(new AttemptResult(
                            idx, false, null,
                            e.getClass().getSimpleName(), e.getMessage(), 0
                    ));
                    System.out.println("[SCHEDULER ERROR] worker=" + workerId + " : " + e.getMessage());
                } finally {
                    done.countDown();
                }
                return null;
            });
        }

        // 동시 출발
        ready.await();
        start.countDown();
        done.await(30, TimeUnit.SECONDS); // 최대 30초 대기

        var after = innodbRowLockSnapshot();
        printDiff("payment_vs_scheduler", before, after);

        paymentPool.shutdown();
        schedulerPool.shutdown();

        // ── 결과 분석 ──
        System.out.println("\n=== 결과 분석 ===");
        System.out.println("타임아웃/에러 건수: " + timeoutErrors.size());
        timeoutErrors.forEach(System.out::println);

        if (!paymentLatencies.isEmpty()) {
            long avg = (long) paymentLatencies.stream().mapToLong(Long::longValue).average().orElse(0);
            long max = paymentLatencies.stream().mapToLong(Long::longValue).max().orElse(0);
            System.out.printf("결제 latency — 평균: %dms, 최대: %dms%n", avg, max);
        }

        // ── 상태 정합성 검증 ──
        long successCnt = 0, failedCnt = 0;
        for (TestCase tc : cases) {
            try {
                PaymentService.PaymentDetailView detail =
                        paymentService.getPaymentDetailForVerification(tc.idemKey(), tc.reservationId());

                if (detail.paymentStatus() == PaymentsStatus.SUCCESS) {
                    successCnt++;
                    assertEquals(ReservationStatus.CONFIRMED, detail.reservationStatus());
                    assertEquals(SeatStatus.CONFIRMED, detail.seatStatus());
                } else {
                    failedCnt++;
                }
            } catch (Exception e) {
                System.out.println("[검증 실패] " + tc.idemKey() + " : " + e.getMessage());
            }
        }
        System.out.printf("payment success=%d, failed=%d%n", successCnt, failedCnt);

        // 스케줄러 결과 검증
        List<UUID> scheduledIds = scheduleResults.stream()
                .filter(AttemptResult::success)
                .flatMap(res -> Arrays.stream(res.reservationId().split(",")))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(UUID::fromString)
                .toList();
        verifyScheduleState(scheduledIds);
    }

    @FunctionalInterface
    interface ThrowingIntConsumer {
        void accept(int idx) throws Exception;
    }

    record TestCase(String userId, UUID reservationId, BigDecimal amount, String idemKey) {}


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

    private void printScheduleMetrics(String label, long tookMs, List<AttemptResult> results) {

        // 성공한 reservationId(콤마 문자열)에서 총 처리 건수 집계
        long processed = results.stream()
                .filter(AttemptResult::success)
                .map(AttemptResult::reservationId)
                .filter(Objects::nonNull)
                .flatMap(s -> Arrays.stream(s.split(",")))
                .map(String::trim)
                .filter(x -> !x.isEmpty())
                .count();

        long successThreads = results.stream().filter(AttemptResult::success).count();
        long failThreads = results.size() - successThreads;

        // latency stats
        List<Long> latencies = results.stream()
                .filter(AttemptResult::success)
                .map(AttemptResult::tookMs)
                .sorted()
                .toList();

        long avg = latencies.isEmpty() ? 0 : (long) latencies.stream().mapToLong(x -> x).average().orElse(0);
        long p95 = percentile(latencies, 95);
        long p99 = percentile(latencies, 99);
        long max = latencies.isEmpty() ? 0 : latencies.get(latencies.size() - 1);

        double throughput = tookMs == 0 ? 0 : (processed * 1000.0 / tookMs);

        System.out.printf(
                "[%s] tookMs=%d, threads ok=%d fail=%d, processed=%d, throughput=%.2f/s, avg=%dms p95=%dms p99=%dms max=%dms%n",
                label, tookMs, successThreads, failThreads, processed, throughput, avg, p95, p99, max
        );
    }

    private long percentile(List<Long> sorted, int p) {
        if (sorted == null || sorted.isEmpty()) return 0;
        int idx = (int) Math.ceil((p / 100.0) * sorted.size()) - 1;
        idx = Math.max(0, Math.min(idx, sorted.size() - 1));
        return sorted.get(idx);
    }


}
