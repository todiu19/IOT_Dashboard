package com.iot.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "data_sensor")
public class SensorData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idsensor", nullable = false)
    private Sensor sensor;

    @Column(name = "\"value\"", nullable = false, precision = 10, scale = 2)
    private BigDecimal value;

    @Column(name = "\"datetime\"", nullable = false)
    private LocalDateTime recordedAt;

    protected SensorData() {
    }

    public SensorData(Sensor sensor, BigDecimal value, LocalDateTime recordedAt) {
        this.sensor = sensor;
        this.value = value;
        this.recordedAt = recordedAt;
    }

    public Integer getId() { return id; }
    public Sensor getSensor() { return sensor; }
    public BigDecimal getValue() { return value; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
}
