package com.iot.domain.entity;

import com.iot.common.enums.DeviceState;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "alert_history", indexes = @Index(name = "idx_alert_history_time", columnList = "occurred_at"))
public class AlertHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 255)
    private DeviceState state;

    @Column(name = "occurred_at", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime occurredAt;

    protected AlertHistory() {}

    public AlertHistory(DeviceState state, LocalDateTime occurredAt) {
        this.state = state;
        this.occurredAt = occurredAt;
    }
}
