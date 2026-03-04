package com.example.reserve.event.consumer;

import com.example.reserve.domain.PaymentService;
import com.example.reserve.domain.SeatService;
import com.example.reserve.model.event.PaymentCompletedEvent;
import com.example.reserve.model.event.PaymentFailedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final PaymentService paymentService;
    private final SeatService seatService;
=
    @KafkaListener(topics = "payment.failed", groupId = "dlq-group")
    public void handleDLQ(PaymentFailedEvent event, Acknowledgment ack) {
        try {
            if (!event.retryable()) {
                // 재시도 불가 케이스는 그냥 ack
                ack.acknowledge();
                return;
            }

            if (event.retryCount() < 3) {
                // 지수 백오프 대기
                long delayMs = (long) Math.pow(2, event.retryCount()) * 1000;
                Thread.sleep(delayMs);

                // 새 idemKey로 재시도
                paymentService.retryPayment(
                        event.reservationId(),
                        event.amount(),
                        event.retryCount() + 1
                );

            } else {
                // 최종 실패 → 좌석 복구
                seatService.releaseSeats(event.reservationId());
            }

            ack.acknowledge();

        } catch (Exception e) {
            // 컨슈머 자체 실패 → ack 안 하면 재처리
            System.out.println("[DLQ ERROR] " + e.getMessage());
        }
    }
}