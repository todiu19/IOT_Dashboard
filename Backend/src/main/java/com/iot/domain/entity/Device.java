package com.iot.domain.entity;

import com.iot.common.enums.DeviceState;

import jakarta.persistence.*;

@Entity
@Table(name = "devices")
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "devicename", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 255)
    private DeviceState status;

    protected Device() {
    }

    public Device(String name, DeviceState status) {
        this.name = name;
        this.status = status;
    }

    public void changeStatus(DeviceState status) {
        this.status = status;
    }

    public Integer getId() { return id; }
    public String getName() { return name; }
    public DeviceState getStatus() { return status; }
}
