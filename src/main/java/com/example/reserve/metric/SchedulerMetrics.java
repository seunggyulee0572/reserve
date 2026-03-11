package com.example.reserve.metric;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class SchedulerMetrics {

    private final MeterRegistry meterRegistry;

    public SchedulerMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void run(String scenario) {
        Counter.builder("scheduler_run_total")
                .description("Scheduler run count")
                .tag("scenario", scenario)
                .register(meterRegistry)
                .increment();
    }

    public void success(String scenario) {
        Counter.builder("scheduler_success_total")
                .description("Scheduler success count")
                .tag("scenario", scenario)
                .register(meterRegistry)
                .increment();
    }

    public void fail(String scenario, String reason) {
        Counter.builder("scheduler_fail_total")
                .description("Scheduler fail count")
                .tag("scenario", scenario)
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    public void processed(String scenario, int count) {
        DistributionSummary.builder("scheduler_processed_total")
                .description("Scheduler processed item count")
                .tag("scenario", scenario)
                .register(meterRegistry)
                .record(count);
    }

    public void recordDuration(String scenario, long millis) {
        Timer.builder("scheduler_run_duration_seconds")
                .description("Scheduler run duration")
                .tag("scenario", scenario)
                .register(meterRegistry)
                .record(millis, TimeUnit.MILLISECONDS);
    }
}