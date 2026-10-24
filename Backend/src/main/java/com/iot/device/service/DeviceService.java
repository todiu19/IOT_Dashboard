package com.iot.device.service;

import com.iot.config.MqttProperties;
import com.iot.domain.entity.User;
import com.iot.domain.entity.Device;
import com.iot.domain.entity.ActionHistory;
import com.iot.common.enums.ActionResult;
import com.iot.common.enums.DeviceState;
import com.iot.common.dto.response.ControlResponse;
import com.iot.common.dto.response.DeviceResponse;
import com.iot.common.exception.ApiException;
import com.iot.mqtt.DeviceStatusEvent;
import com.iot.mqtt.MqttClientService;
import com.iot.domain.repository.UserRepository;
import com.iot.domain.repository.DeviceRepository;
import com.iot.domain.repository.ActionHistoryRepository;
import com.iot.domain.repository.AlertHistoryRepository;
import com.iot.domain.entity.AlertHistory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import com.iot.common.exception.ErrorCode;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);
    private static final String AUTOMATION_USERNAME = "iot-system";

    private final DeviceRepository devices;
    private final ActionHistoryRepository histories;
    private final AlertHistoryRepository alertHistories;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final MqttClientService mqtt;
    private final MqttProperties mqttProperties;
    private final TaskScheduler scheduler;
    private final TransactionTemplate transactions;
    private final SimpMessagingTemplate messaging;

    public DeviceService(
            DeviceRepository devices,
            ActionHistoryRepository histories,
            AlertHistoryRepository alertHistories,
            UserRepository users,
            PasswordEncoder passwords,
            MqttClientService mqtt,
            MqttProperties mqttProperties,
            @Qualifier("taskScheduler") TaskScheduler scheduler,
            PlatformTransactionManager transactionManager,
            SimpMessagingTemplate messaging
    ) {
        this.devices = devices;
        this.histories = histories;
        this.alertHistories = alertHistories;
        this.users = users;
        this.passwords = passwords;
        this.mqtt = mqtt;
        this.mqttProperties = mqttProperties;
        this.scheduler = scheduler;
        this.transactions = new TransactionTemplate(transactionManager);
        this.messaging = messaging;
    }

    public ControlResponse control(Integer deviceId, DeviceState action, String username) {
        if (Integer.valueOf(4).equals(deviceId)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Đèn cảnh báo được backend tự động điều khiển theo ngưỡng ánh sáng.");
        }
        // Commit LOADING trước khi gửi MQTT để phản hồi nhanh vẫn tìm thấy lịch sử lệnh.
        ControlResponse loading = transactions.execute(status -> {
            Device device = devices.findByIdForUpdate(deviceId)
                    .orElseThrow(() -> new ApiException(ErrorCode.DEVICE_NOT_FOUND));
            User user = users.findByUsername(username)
                    .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
            if (histories.findFirstByDeviceIdAndStatusOrderByPerformedAtDesc(deviceId, ActionResult.LOADING).isPresent()) {
                throw new ApiException(ErrorCode.DEVICE_BUSY);
            }
            ActionHistory history = histories.save(new ActionHistory(device, user, action));
            ControlResponse response = toControlResponse(history);
            sendAfterCommit("/topic/status", response);
            return response;
        });

        if (!mqtt.isEnabled()) {
            return transactions.execute(status -> {
                Device device = devices.findByIdForUpdate(deviceId).orElseThrow();
                ActionHistory history = histories.findById(loading.historyId()).orElseThrow();
                complete(device, history, action);
                return toControlResponse(history);
            });
        }

        Instant deadline = deadline(loading);
        try {
            mqtt.publishControl(deviceId, action);
            scheduler.schedule(() -> timeout(loading.historyId(), deviceId), deadline);
        } catch (ApiException exception) {
            timeout(loading.historyId(), deviceId);
            throw exception;
        }
        return loading;
    }

    @EventListener
    @Transactional
    public void receiveDeviceStatus(DeviceStatusEvent event) {
        Device device = devices.findByIdForUpdate(event.deviceId())
                .orElseThrow(() -> new ApiException(ErrorCode.DEVICE_NOT_FOUND, "Không tìm thấy thiết bị MQTT."));
        ActionHistory history = histories
                .findFirstByDeviceIdAndStatusOrderByPerformedAtDesc(event.deviceId(), ActionResult.LOADING)
                .orElse(null);
        if (event.deviceId() == 4 && device.getStatus() != event.status()) {
            alertHistories.save(new AlertHistory(event.status(), LocalDateTime.now()));
            if (history == null) {
                User system = users.findByUsername(AUTOMATION_USERNAME).orElseGet(() ->
                        users.save(new User("Hệ thống", AUTOMATION_USERNAME,
                                passwords.encode(UUID.randomUUID().toString()), "SYSTEM", null)));
                history = histories.save(new ActionHistory(device, system, event.status()));
            }
        }
        device.changeStatus(event.status());
        if (history != null) {
            if (Instant.now().isAfter(deadline(toControlResponse(history)))) history.fail(ActionResult.FAILED);
            else history.complete(event.status());
            sendAfterCommit("/topic/status", toControlResponse(history));
        }
        sendAfterCommit("/topic/devices", toResponse(device));
    }

    private void complete(Device device, ActionHistory history, DeviceState state) {
        device.changeStatus(state);
        history.complete(state);
        sendAfterCommit("/topic/status", toControlResponse(history));
        sendAfterCommit("/topic/devices", toResponse(device));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumePendingControls() {
        var pending = transactions.execute(status -> histories.findByStatusOrderByPerformedAtAsc(ActionResult.LOADING)
                .stream().map(DeviceService::toControlResponse).toList());
        for (ControlResponse control : pending) {
            scheduler.schedule(() -> timeout(control.historyId(), control.deviceId()), deadline(control));
        }
    }

    private Instant deadline(ControlResponse control) {
        return control.performedAt().atZone(ZoneId.systemDefault()).toInstant().plus(mqttProperties.controlTimeout());
    }

    private void timeout(Integer historyId, Integer deviceId) {
        transactions.executeWithoutResult(status -> {
            // Cùng khóa với receiveDeviceStatus: timeout và ACK không ghi đè kết quả của nhau.
            devices.findByIdForUpdate(deviceId).orElseThrow();
            histories.findById(historyId).ifPresent(history -> {
                if (history.getStatus() == ActionResult.LOADING) {
                    history.fail(ActionResult.FAILED);
                    sendAfterCommit("/topic/status", toControlResponse(history));
                }
            });
        });
    }

    private void sendAfterCommit(String topic, Object payload) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    messaging.convertAndSend(topic, payload);
                } catch (RuntimeException exception) {
                    log.warn("Không thể gửi trạng thái WebSocket tới {}", topic, exception);
                }
            }
        });
    }

    public static DeviceResponse toResponse(Device device) {
        return new DeviceResponse(device.getId(), device.getName(), device.getStatus());
    }

    public static ControlResponse toControlResponse(ActionHistory history) {
        return new ControlResponse(
                history.getId(),
                history.getDevice().getId(),
                history.getDevice().getName(),
                history.getAction(),
                history.getStatus(),
                history.getPerformedAt()
        );
    }
}
