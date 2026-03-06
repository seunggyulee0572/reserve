package com.example.reserve.event.consumer;

import com.example.reserve.domain.PaymentService;
import com.example.reserve.domain.ReservationService;
import com.example.reserve.domain.SeatService;
import com.example.reserve.model.enums.FailureReason;
import com.example.reserve.model.enums.PaymentsStatus;
import com.example.reserve.model.event.PaymentCompletedEvent;
import com.example.reserve.model.event.PaymentFailedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final PaymentService paymentService;
    private final ReservationService reservationService;

    @KafkaListener(topics = "payment.failed", groupId = "dlq-group",
            containerFactory = "dlqKafkaListenerContainerFactory")
    public void handleDLQ(PaymentFailedEvent event, Acknowledgment ack) {
        try {
            System.out.println("[DLQ] reservationId=" + event.reservationId()
                    + " retryCount=" + event.retryCount()
                    + " retryable=" + event.retryable());

            if (!event.retryable()) {
                ack.acknowledge();
                return;
            }

            if (event.retryCount() < 2) {
                long delayMs = (long) Math.pow(2, event.retryCount()) * 1000;
                Thread.sleep(delayMs);

                String newIdemKey = paymentService.retryPayment(
                        event.reservationId(),
                        event.amount(),
                        event.retryCount() + 1
                );

                paymentService.processPayment(
                        "pg-retry-" + UUID.randomUUID(),
                        newIdemKey,
                        event.amount(),
                        PaymentsStatus.FAILED,
                        FailureReason.PG_ERROR
                );

            } else {
                // retryCount >= 3 → 최종 실패
                // 예외 터져도 ack 하도록 try-catch로 감쌈
                try {
                    reservationService.releaseSeatsByReservationId(event.reservationId());
                } catch (Exception e) {
                    System.out.println("[DLQ] 좌석 복구 실패: " + e.getMessage());
                    // 좌석 복구 실패해도 ack는 해야 무한루프 방지
                }
            }

            ack.acknowledge();  // 항상 ack

        } catch (Exception e) {
            System.out.println("[DLQ ERROR] " + e.getMessage());
            // ack 안 하면 재처리 → 의도적 재처리만 여기서 처리
            ack.acknowledge();  // 일단 ack해서 무한루프 방지
        }
    }
}