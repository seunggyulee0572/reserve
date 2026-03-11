package com.example.reserve.model.dto;

public record ScenarioResponse(
        boolean success,
        String scenario,
        String message,
        Object data
) {
    public static ScenarioResponse ok(String scenario, String message, Object data) {
        return new ScenarioResponse(true, scenario, message, data);
    }

    public static ScenarioResponse fail(String scenario, String message) {
        return new ScenarioResponse(false, scenario, message, null);
    }
}