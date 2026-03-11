package com.example.reserve.metric;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ReservationMetrics {

    private final MeterRegistry meterRegistry;

    public ReservationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void request(String scenario) {
        Counter.builder("reservation_requests_total")
                .description("Reservation request count")
                .tag("scenario", scenario)
                .register(meterRegistry)
                .increment();
    }

    public void success(String scenario) {
        Counter.builder("reservation_success_total")
                .description("Reservation success count")
                .tag("scenario", scenario)
                .register(meterRegistry)
                .increment();
    }

    public void fail(String scenario, String reason) {
        Counter.builder("reservation_fail_total")
                .description("Reservation fail count")
                .tag("scenario", scenario)
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    public void optimisticLockFail(String scenario) {
        Counter.builder("reservation_optimistic_lock_fail_total")
                .description("Reservation optimistic lock failure count")
                .tag("scenario", scenario)
                .register(meterRegistry)
                .increment();
    }
}