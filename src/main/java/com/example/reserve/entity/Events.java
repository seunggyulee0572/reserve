package com.example.reserve.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
public class Events {

    @Id
    @GeneratedValue( strategy = GenerationType.UUID)
    private UUID id;

    private String title;
    private LocalDateTime eventDate;
    private Integer totalSeats;
    private Integer availableSeats;
    private Integer version;
    private LocalDateTime createdAt;

    @OneToMany( fetch =  FetchType.LAZY, cascade = CascadeType.ALL, mappedBy = "event")
    private List<Seats> seats;
}
