package com.example.reserve.event.producer;

import com.example.reserve.entity.Payments;
import com.example.reserve.model.event.PaymentCompletedEvent;
import com.example.reserve.model.event.PaymentFailedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishPaymentCompleted(Payments payment) {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                payment.getId(),
                payment.getReservation().getEvent().getId(),
                payment.getReservation().getId(),
                payment.getAmount(),
                LocalDateTime.now()
        );
        kafkaTemplate.send("payment.completed",
                payment.getReservation().getId().toString(),
                event
        );
    }

    public void publishPaymentFailed(Payments payment, int retryCount) {
        PaymentFailedEvent event = new PaymentFailedEvent(
                payment.getId(),
                payment.getReservation().getId(),
                payment.getReservation().getEvent().getId(),
                payment.getAmount(),
                payment.getIdempotencyKey(),
                payment.getFailureReason(),
                retryCount,
                LocalDateTime.now()
        );
        kafkaTemplate.send("payment.failed",
                payment.getReservation().getId().toString(),
                event
        );
    }
}