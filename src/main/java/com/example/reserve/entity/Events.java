package com.example.reserve.entity;

import jakarta.persistence.*;import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "events")
public class Events {

    @Id
    @GeneratedValue( strategy = GenerationType.UUID)
    private UUID id;

    private String title;
    private LocalDateTime eventDate;
    private Integer totalSeats;
    @Column( name = "available_seats")
    private Integer availableSeats;
    @Version
    private Integer version;
    private LocalDateTime createdAt;

    @OneToMany( fetch =  FetchType.LAZY, cascade = CascadeType.ALL, mappedBy = "event")
    private List<Seats> seats;
}
