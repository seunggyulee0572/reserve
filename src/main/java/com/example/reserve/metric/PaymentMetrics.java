package com.example.reserve.metric;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class PaymentMetrics {

    private final MeterRegistry meterRegistry;

    public PaymentMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void request() {
        Counter.builder("payment_request_total")
                .description("Payment request count")
                .register(meterRegistry)
                .increment();
    }

    public void success() {
        Counter.builder("payment_success_total")
                .description("Payment success count")
                .register(meterRegistry)
                .increment();
    }

    public void fail(String reason) {
        Counter.builder("payment_fail_total")
                .description("Payment fail count")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    public void recordProcessDuration(long millis) {
        Timer.builder("payment_process_duration_seconds")
                .description("Payment processing duration")
                .register(meterRegistry)
                .record(millis, TimeUnit.MILLISECONDS);
    }
}