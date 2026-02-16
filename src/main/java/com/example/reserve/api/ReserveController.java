package com.example.reserve.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController("/api/v1")
public class ReserveController {


    @PostMapping("/events/{eventId}/reservations")
    public void reserve(@PathVariable UUID eventId){

    }


    /**
     * Test 용 (1개만 빠르게 생성)
     *
     */
    @PostMapping("/events")
    public void createEvent(){


    }
}
