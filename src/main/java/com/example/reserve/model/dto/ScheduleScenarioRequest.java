package com.example.reserve.model.dto;

import java.util.UUID;

public record ScheduleScenarioRequest(
        int limit,
        String workerId
) {
}