package com.example.reserve.domain;

import com.example.reserve.entity.Events;
import com.example.reserve.entity.Seats;
import com.example.reserve.model.enums.SeatStatus;
import com.example.reserve.repository.EventsRepository;
import com.example.reserve.repository.SeatsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SeatService {

    private final SeatsRepository seatsRepository;
    private final EventsRepository eventsRepository;
    public SeatService(SeatsRepository seatsRepository,
                       EventsRepository eventsRepository) {
        this.seatsRepository = seatsRepository;
        this.eventsRepository = eventsRepository;
    }

    // Todo: seat 1000개 만들기, A-J * 100
    @Transactional
    public void makeSeat(UUID eventId){

        Events event = eventsRepository.getReferenceById(eventId);

        String[] args = {"A","B","C","D","E","F","G","H","I","J"};

        List<Seats> list = new ArrayList<>(1000);
        for (int i = 1; i <= 100; i++) {
            for( String name : args){
                Seats seat = new Seats();
                seat.setSeatStatus(SeatStatus.AVAILABLE);
                seat.setSeatNumber(i + "-" + name);
                seat.setEvent(event);
                list.add(seat);
            }
        }

        seatsRepository.saveAll(list);
    }
}
