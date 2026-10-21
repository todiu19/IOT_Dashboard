package com.iot.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "sensors")
public class Sensor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "sensorname", nullable = false, length = 255)
    private String name;

    protected Sensor() {
    }

    public Sensor(String name) {
        this.name = name;
    }

    public Integer getId() { return id; }
    public String getName() { return name; }
}
