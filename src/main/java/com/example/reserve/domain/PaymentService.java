package com.example.reserve.domain;

import com.example.reserve.entity.Payments;
import com.example.reserve.entity.Reservations;
import com.example.reserve.model.enums.PaymentsStatus;
import com.example.reserve.model.enums.ReservationStatus;
import com.example.reserve.model.enums.SeatStatus;
import com.example.reserve.repository.EventsRepository;
import com.example.reserve.repository.PaymentRepository;
import com.example.reserve.repository.ReservationsRepository;
import com.example.reserve.repository.SeatsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoLocalDateTime;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final EventsRepository eventsRepository;
    private final ReservationsRepository reservationsRepository;
    public PaymentService(PaymentRepository paymentRepository,
                          EventsRepository eventsRepository,
                          ReservationsRepository reservationsRepository) {
        this.paymentRepository = paymentRepository;
        this.eventsRepository = eventsRepository;
        this.reservationsRepository = reservationsRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requestPayment(UUID reservationId, BigDecimal amount, String idempotencyKey) {
        // 1. 이미 해당 멱등성 키로 저장된 결제 시도가 있는지 확인 (중복 요청 방지)
        if (paymentRepository.existsPaymentsByIdempotencyKey(idempotencyKey)) {
            return;
        }

        Reservations reservation = reservationsRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("예약 정보 없음"));

        // 2. 만료시간 끝났는지 체크
        if(reservation.getStatus() == ReservationStatus.PENDING &&
                LocalDateTime.now().isAfter(reservation.getExpiresAt()))
            throw new IllegalArgumentException("결제 시간 만료.");

        // 3. DB에 저장된 예약 금액과 요청받은 금액이 일치하는지 최종 검증
        if (reservation.getTotalAmount().compareTo(amount) != 0) {
            throw new IllegalArgumentException("결제 금액이 일치하지 않습니다.");
        }

        Payments payments = new Payments();
        payments.setAmount(amount);
        payments.setReservation(reservation);
        payments.setStatus(PaymentsStatus.PENDING);
        payments.setIdempotencyKey(idempotencyKey);

        paymentRepository.saveAndFlush(payments);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processPayment(String paymentKey,
                               String idempotencyKey,
                               BigDecimal pgAmount,
                               PaymentsStatus status) {

        Payments payment = paymentRepository.findPaymentsByIdempotencyKeyAndStatus(idempotencyKey, PaymentsStatus.PENDING)
                .orElseThrow(() -> new IllegalArgumentException("결제 시도 내역이 없음"));

        // PG사에서 실제 결제된 금액(pgAmount)과 우리 DB의 금액이 같은지 다시 확인
        if (payment.getAmount().compareTo(pgAmount) != 0) {

            payment.setStatus(PaymentsStatus.FAILED);
            System.out.println("결제 금액과 요청한 금액이 다릅니다.");
            return;
        }

        payment.setPaymentKey(paymentKey);
        payment.setStatus(status);

        if (status == PaymentsStatus.SUCCESS) {
            payment.getReservation().setStatus(ReservationStatus.CONFIRMED);
            payment.getReservation().getSeats().setSeatStatus(SeatStatus.CONFIRMED);

        }
    }

    @Transactional
    public Payments findPaymentByIdemKey( String idemKey ){

       return paymentRepository.findPaymentsByIdempotencyKey( idemKey )
                .orElseThrow( () -> new IllegalArgumentException(idemKey + " no"));

    }

    @Transactional(readOnly = true)
    public PaymentDetailView getPaymentDetailForVerification(String idemKey, UUID reservationId) {
        // 1) Payment 조회
        Payments p = paymentRepository.findPaymentsByIdempotencyKey( idemKey )
                .orElseThrow(() -> new RuntimeException("Payment not found for key: " + idemKey));

        // 2) Reservation 조회 (이때 Seats도 함께 사용되므로 트랜잭션 내에서 접근)
        Reservations r = reservationsRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found for id: " + reservationId));

        // 3) 검증에 필요한 데이터들을 DTO에 담아서 반환
        // (Seats의 상태를 호출함으로써 강제 초기화 발생)
        return new PaymentDetailView(
                p.getStatus(),
                r.getStatus(),
                r.getSeats().getSeatStatus()
        );
    }

    // 검증용 간단한 DTO (Record 사용 추천)
    public record PaymentDetailView(
            PaymentsStatus paymentStatus,
            ReservationStatus reservationStatus,
            SeatStatus seatStatus
    ) {}
}
