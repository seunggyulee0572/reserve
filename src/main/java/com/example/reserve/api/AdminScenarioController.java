package com.example.reserve.api;

import com.example.reserve.domain.AdminScenarioService;
import com.example.reserve.model.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/scenarios")
@RequiredArgsConstructor
public class AdminScenarioController {

    private final AdminScenarioService adminScenarioService;

    @PostMapping("/init/event")
    public ResponseEntity<ScenarioResponse> initEvent() {
        return ResponseEntity.ok(adminScenarioService.initEvent());
    }

    @PostMapping("/init/seats/{eventId}")
    public ResponseEntity<ScenarioResponse> initSeats(@PathVariable UUID eventId) {
        return ResponseEntity.ok(adminScenarioService.initSeats(eventId));
    }

    @PostMapping("/reservation/pessimistic")
    public ResponseEntity<ScenarioResponse> reservePessimistic(@RequestBody ReservationScenarioRequest request) {
        return ResponseEntity.ok(adminScenarioService.reservePessimistic(request));
    }

    @PostMapping("/reservation/no-lock")
    public ResponseEntity<ScenarioResponse> reserveNoLock(@RequestBody ReservationScenarioRequest request) {
        return ResponseEntity.ok(adminScenarioService.reserveNoLock(request));
    }

    @PostMapping("/reservation/atomic-update")
    public ResponseEntity<ScenarioResponse> reserveAtomicUpdate(@RequestBody ReservationScenarioRequest request) {
        return ResponseEntity.ok(adminScenarioService.reserveAtomicUpdate(request));
    }

    @PostMapping("/reservation/optimistic")
    public ResponseEntity<ScenarioResponse> reserveOptimistic(@RequestBody ReservationScenarioRequest request) {
        return ResponseEntity.ok(adminScenarioService.reserveOptimistic(request));
    }

    @PostMapping("/reservation/expired")
    public ResponseEntity<ScenarioResponse> generateExpired(@RequestBody ReservationScenarioRequest request) {
        return ResponseEntity.ok(adminScenarioService.generateExpired(request));
    }

    @PostMapping("/schedule/naive")
    public ResponseEntity<ScenarioResponse> scheduleNaive(@RequestBody ScheduleScenarioRequest request) {
        return ResponseEntity.ok(adminScenarioService.scheduleNaive(request));
    }

    @PostMapping("/schedule/select-for-update")
    public ResponseEntity<ScenarioResponse> scheduleSelectForUpdate(@RequestBody ScheduleScenarioRequest request) {
        return ResponseEntity.ok(adminScenarioService.scheduleSelectForUpdate(request));
    }

    @PostMapping("/schedule/select-for-update-skip")
    public ResponseEntity<ScenarioResponse> scheduleSelectForUpdateSkip(@RequestBody ScheduleScenarioRequest request) {
        return ResponseEntity.ok(adminScenarioService.scheduleSelectForUpdateSkip(request));
    }

    @PostMapping("/schedule/select-for-update-skip-batch")
    public ResponseEntity<ScenarioResponse> scheduleSelectForUpdateSkipBatch(@RequestBody ScheduleScenarioRequest request) {
        return ResponseEntity.ok(adminScenarioService.scheduleSelectForUpdateSkipBatch(request));
    }

    @PostMapping("/schedule/claim-update")
    public ResponseEntity<ScenarioResponse> scheduleClaimUpdate(@RequestBody ScheduleScenarioRequest request) {
        return ResponseEntity.ok(adminScenarioService.scheduleClaimUpdate(request));
    }

    @PostMapping("/payment/request")
    public ResponseEntity<ScenarioResponse> requestPayment(@RequestBody PaymentRequestDto request) {
        return ResponseEntity.ok(adminScenarioService.requestPayment(request));
    }

    @PostMapping("/payment/process")
    public ResponseEntity<ScenarioResponse> processPayment(@RequestBody PaymentProcessRequest request) {
        return ResponseEntity.ok(adminScenarioService.processPayment(request));
    }

    @GetMapping("/payment/detail")
    public ResponseEntity<ScenarioResponse> paymentDetail(
            @RequestParam String idemKey,
            @RequestParam UUID reservationId
    ) {
        return ResponseEntity.ok(adminScenarioService.paymentDetail(idemKey, reservationId));
    }
}