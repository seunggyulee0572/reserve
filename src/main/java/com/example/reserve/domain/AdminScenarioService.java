package com.example.reserve.domain;

import com.example.reserve.domain.*;
import com.example.reserve.entity.Events;
import com.example.reserve.entity.Reservations;
import com.example.reserve.entity.Seats;
import com.example.reserve.job.NaiveJob;
import com.example.reserve.job.SelectForUpdateJob;
import com.example.reserve.job.SelectForUpdateSkipJob;
import com.example.reserve.job.UpdateFirstJob;
import com.example.reserve.metric.PaymentMetrics;
import com.example.reserve.metric.ReservationMetrics;
import com.example.reserve.metric.SchedulerMetrics;
import com.example.reserve.model.dto.*;
import com.example.reserve.model.enums.ReservationStatus;
import com.example.reserve.model.enums.SeatStatus;
import com.example.reserve.repository.EventsRepository;
import com.example.reserve.repository.ReservationsRepository;
import com.example.reserve.repository.SeatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminScenarioService {

    private final ReservationService reservationService;
    private final PaymentService paymentService;
    private final EventService eventService;
    private final SeatService seatService;

    private final NaiveJob naiveJob;
    private final SelectForUpdateJob selectForUpdateJob;
    private final SelectForUpdateSkipJob selectForUpdateSkipJob;
    private final UpdateFirstJob updateFirstJob;

    private final SeatsRepository seatsRepository;
    private final EventsRepository eventsRepository;
    private final ReservationsRepository reservationsRepository;

    private final ReservationMetrics reservationMetrics;
    private final PaymentMetrics paymentMetrics;
    private final SchedulerMetrics schedulerMetrics;

    @Transactional
    public ScenarioResponse initEvent() {
        UUID uuid = eventService.generateEvent();
        return ScenarioResponse.ok("init-event", "이벤트 생성 완료", uuid);
    }

    @Transactional
    public ScenarioResponse initSeats(UUID eventId) {
        seatService.makeSeat(eventId);
        return ScenarioResponse.ok("init-seats", "좌석 생성 완료", eventId);
    }

    @Transactional
    public ScenarioResponse reservePessimistic(ReservationScenarioRequest request) {
        String scenario = "pessimistic";

        reservationMetrics.request(scenario);

        try {
            UUID reservationId = reservationService.generateReservation(
                    request.eventId(),
                    request.seatNumber(),
                    request.userId()
            );

            reservationMetrics.success(scenario);

            return ScenarioResponse.ok(
                    "reserve-pessimistic",
                    "비관적 락 예약 완료",
                    reservationId
            );
        } catch (Exception e) {
            reservationMetrics.fail(scenario, normalizeReservationReason(e));
            throw e;
        }
    }

    @Transactional
    public ScenarioResponse reserveNoLock(ReservationScenarioRequest request) {

        UUID reservationId = reservationService.generateReservationNoLock(
                request.eventId(),
                request.seatNumber(),
                request.userId()
        );

        return ScenarioResponse.ok(
                "reserve-no-lock",
                "락 없는 예약 완료",
                toReservationResult(request.eventId(), request.seatNumber(), request.userId(), reservationId)
        );
    }

    @Transactional
    public ScenarioResponse reserveAtomicUpdate(ReservationScenarioRequest request) {
        String scenario = "atomic";

        reservationMetrics.request(scenario);
        try {


            UUID reservationId = reservationService.generateReservationAtomicUpdate(
                    request.eventId(),
                    request.seatNumber(),
                    request.userId()
            );

            reservationMetrics.success(scenario);

            return ScenarioResponse.ok(
                    "reserve-atomic-update",
                    "원자적 업데이트 예약 완료",
                    toReservationResult(request.eventId(), request.seatNumber(), request.userId(), reservationId)
            );
        } catch ( Exception e ){
            reservationMetrics.fail(scenario, normalizeReservationReason(e));
            throw e;
        }
    }

    @Transactional
    public ScenarioResponse reserveOptimistic(ReservationScenarioRequest request) {
        String scenario = "optimistic";

        reservationMetrics.request(scenario);

        try {
            UUID reservationId = reservationService.generateReservationOptimistic(
                    request.eventId(),
                    request.seatNumber(),
                    request.userId()
            );

            reservationMetrics.success(scenario);

            return ScenarioResponse.ok(
                    "reserve-optimistic",
                    "낙관적 락 예약 완료",
                    reservationId
            );
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            reservationMetrics.optimisticLockFail(scenario);
            reservationMetrics.fail(scenario, "optimistic_lock");
            throw e;
        } catch (Exception e) {
            reservationMetrics.fail(scenario, normalizeReservationReason(e));
            throw e;
        }
    }

    @Transactional
    public ScenarioResponse generateExpired(ReservationScenarioRequest request) {
        UUID reservationId = reservationService.generateExpired(
                request.eventId(),
                request.seatNumber(),
                request.userId()
        );

        return ScenarioResponse.ok(
                "generate-expired",
                "만료 예약 생성 완료",
                toReservationResult(request.eventId(), request.seatNumber(), request.userId(), reservationId)
        );
    }

    @Transactional
    public ScenarioResponse scheduleNaive(ScheduleScenarioRequest request) {
        List<UUID> ids = naiveJob.runOnce(request.limit(), request.workerId());

        return ScenarioResponse.ok(
                "schedule-naive",
                "naive 스케줄러 실행 완료",
                new ScheduleScenarioResult(request.workerId(), ids.size(), ids)
        );
    }

    @Transactional
    public ScenarioResponse scheduleSelectForUpdate(ScheduleScenarioRequest request) {
        List<UUID> ids = selectForUpdateJob.runOnce(request.limit(), request.workerId());

        return ScenarioResponse.ok(
                "schedule-select-for-update",
                "select for update 스케줄러 실행 완료",
                new ScheduleScenarioResult(request.workerId(), ids.size(), ids)
        );
    }

    @Transactional
    public ScenarioResponse scheduleSelectForUpdateSkip(ScheduleScenarioRequest request) {
        List<UUID> ids = selectForUpdateSkipJob.runOnce(request.limit(), request.workerId());

        return ScenarioResponse.ok(
                "schedule-select-for-update-skip",
                "skip locked 스케줄러 실행 완료",
                new ScheduleScenarioResult(request.workerId(), ids.size(), ids)
        );
    }

    @Transactional
    public ScenarioResponse scheduleSelectForUpdateSkipBatch(ScheduleScenarioRequest request) {
        List<UUID> ids = selectForUpdateSkipJob.runMulti(request.limit(), request.workerId());

        return ScenarioResponse.ok(
                "schedule-select-for-update-skip-batch",
                "skip locked batch 스케줄러 실행 완료",
                new ScheduleScenarioResult(request.workerId(), ids.size(), ids)
        );
    }

    @Transactional
    public ScenarioResponse scheduleClaimUpdate(ScheduleScenarioRequest request) {
        List<UUID> ids = updateFirstJob.runOnce(request.limit(), request.workerId());

        return ScenarioResponse.ok(
                "schedule-claim-update",
                "claim update 스케줄러 실행 완료",
                new ScheduleScenarioResult(request.workerId(), ids.size(), ids)
        );
    }

    @Transactional
    public ScenarioResponse requestPayment(PaymentRequestDto request) {
        paymentService.requestPayment(
                request.reservationId(),
                request.amount(),
                request.idemKey()
        );

        return ScenarioResponse.ok("payment-request", "결제 요청 완료", request);
    }

    @Transactional
    public ScenarioResponse processPayment(PaymentProcessRequest request) {
        paymentService.processPayment(
                request.pgTxId(),
                request.idemKey(),
                request.amount(),
                request.status(),
                request.failureReason()
        );

        return ScenarioResponse.ok("payment-process", "결제 처리 완료", request);
    }

    @Transactional(readOnly = true)
    public ScenarioResponse paymentDetail(String idemKey, UUID reservationId) {
        PaymentService.PaymentDetailView detail =
                paymentService.getPaymentDetailForVerification(idemKey, reservationId);

        return ScenarioResponse.ok("payment-detail", "결제 상세 조회 완료", detail);
    }

    private ReservationScenarioResult toReservationResult(
            UUID eventId,
            String seatNumber,
            String userId,
            UUID reservationId
    ) {
        Seats seat = seatsRepository.findByEvent_IdAndSeatNumber(eventId, seatNumber)
                .orElseThrow(() -> new IllegalArgumentException("seat not found"));

        Reservations reservation = reservationsRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("reservation not found"));

        return new ReservationScenarioResult(
                reservationId,
                eventId,
                seatNumber,
                userId,
                seat.getSeatStatus(),
                reservation.getStatus()
        );
    }

    private String normalizeReservationReason(Exception e) {
        if (e instanceof org.springframework.orm.ObjectOptimisticLockingFailureException) {
            return "optimistic_lock";
        }
        if (e instanceof IllegalStateException) {
            return "already_reserved";
        }
        if (e instanceof IllegalArgumentException) {
            return "invalid_request";
        }
        if (e instanceof RuntimeException && e.getMessage() != null && e.getMessage().contains("sold out")) {
            return "sold_out";
        }
        return "unknown";
    }

    private String normalizePaymentReason(Exception e) {
        if (e instanceof IllegalArgumentException) {
            return "invalid_request";
        }
        if (e.getMessage() != null && e.getMessage().contains("PG")) {
            return "pg_error";
        }
        return "unknown";
    }

    private String normalizeSchedulerReason(Exception e) {
        String msg = e.getMessage();
        if (msg != null && msg.toLowerCase().contains("timeout")) {
            return "timeout";
        }
        return "unknown";
    }
}