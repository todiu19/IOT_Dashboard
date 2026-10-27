package com.iot.domain.entity;

import com.iot.common.enums.ActionResult;
import com.iot.common.enums.DeviceState;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "action_history")
public class ActionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "iddevice", nullable = false)
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "iduser", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 255)
    private DeviceState action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 255)
    private ActionResult status;

    @Column(name = "\"time\"", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime performedAt;

    protected ActionHistory() {
    }

    public ActionHistory(Device device, User user, DeviceState action) {
        this.device = device;
        this.user = user;
        this.action = action;
        this.status = ActionResult.LOADING;
        this.performedAt = LocalDateTime.now();
    }

    public ActionHistory(Device device, User user, DeviceState action, ActionResult status, LocalDateTime performedAt) {
        this.device = device;
        this.user = user;
        this.action = action;
        this.status = status;
        this.performedAt = performedAt;
    }

    public void complete(DeviceState state) {
        status = ActionResult.valueOf(state.name());
    }

    public void fail(ActionResult result) {
        status = result;
    }

    public Integer getId() { return id; }
    public Device getDevice() { return device; }
    public User getUser() { return user; }
    public DeviceState getAction() { return action; }
    public ActionResult getStatus() { return status; }
    public LocalDateTime getPerformedAt() { return performedAt; }
}
