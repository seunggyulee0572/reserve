package com.example.reserve.model.dto;

import java.util.List;
import java.util.UUID;

public record ScheduleScenarioResult(
        String workerId,
        int processedCount,
        List<UUID> reservationIds
) {
}