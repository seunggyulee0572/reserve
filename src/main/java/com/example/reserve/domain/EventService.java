package com.example.reserve.domain;

import com.example.reserve.entity.Events;
import com.example.reserve.repository.EventsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class EventService {

    private final EventsRepository eventsRepository;

    public EventService(EventsRepository eventsRepository) {
        this.eventsRepository = eventsRepository;
    }

    // todo : 임시로 테스트용 1개 생성
    @Transactional
    public void generateEvent() {

        Events event = new Events();

        event.setTitle("test-seat");
        event.setTotalSeats( 1000 );
        event.setAvailableSeats( 1000 );
        event.setEventDate(LocalDateTime.now());

        eventsRepository.save(event);
    }

    public UUID getEventId() {
        return eventsRepository.findAll().getFirst().getId();
    }

}
