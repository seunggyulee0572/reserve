package com.example.reserve.domain;

import com.example.reserve.entity.Events;
import com.example.reserve.entity.Seats;
import com.example.reserve.model.enums.SeatStatus;
import com.example.reserve.repository.EventsRepository;
import com.example.reserve.repository.SeatsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        String[] grades = {"VIP","A","B","C","D","E","F","G","H","I","J"};
        int[] basePrices = {1_000_000, 900_000, 800_000, 700_000, 1_000_000,
                500_000, 400_000, 300_000, 200_000, 100_000, 50_000 };

        Map<String, Integer> gradeMap = IntStream.range(0, basePrices.length)
                .boxed()
                .collect(Collectors.toMap(i -> grades[i], i -> basePrices[i]));

        List<Seats> list = new ArrayList<>(1000);
        for (int i = 1; i <= 100; i++) {
            for( String name : args){
                Seats seat = new Seats();
                seat.setSeatStatus(SeatStatus.AVAILABLE);
                seat.setSeatNumber(i + "-" + name);
                seat.setEvent(event);
                if(i<=10) {
                    seat.setGrade("VIP");
                    seat.setBasePrice(new BigDecimal(gradeMap.get("VIP")));
                }
                else {
                    seat.setGrade(name);
                    seat.setBasePrice(new BigDecimal(gradeMap.get(name)));
                }
                list.add(seat);
            }
        }

        seatsRepository.saveAll(list);
    }
}
