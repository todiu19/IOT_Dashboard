package com.iot;

import com.iot.common.dto.response.ControlResponse;
import com.iot.common.enums.ActionResult;
import com.iot.common.enums.DeviceState;
import com.iot.common.exception.ApiException;
import com.iot.common.exception.ErrorCode;
import com.iot.dashboard.service.DashboardService;
import com.iot.device.service.DeviceService;
import com.iot.domain.repository.ActionHistoryRepository;
import com.iot.domain.repository.DeviceRepository;
import com.iot.mqtt.DeviceStatusEvent;
import com.iot.mqtt.MqttClientService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:iot_control_status;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@ActiveProfiles("test")
class DeviceControlStatusTests {
    @Autowired private DeviceService service;
    @Autowired private DashboardService dashboard;
    @Autowired private ActionHistoryRepository histories;
    @Autowired private DeviceRepository devices;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private MqttClientService mqtt;
    @MockitoBean(name = "taskScheduler") private TaskScheduler scheduler;
    @MockitoBean private SimpMessagingTemplate messaging;

    private final List<ControlResponse> statuses = new ArrayList<>();

    @BeforeEach
    void resetState() {
        reset(mqtt, scheduler, messaging);
        statuses.clear();
        histories.deleteAll();
        var list = devices.findAll();
        list.forEach(device -> device.changeStatus(DeviceState.OFF));
        devices.saveAll(list);
        when(mqtt.isEnabled()).thenReturn(true);

        // Một transaction mới phải thấy trạng thái đã lưu trước khi FE nhận thông báo.
        doAnswer(invocation -> {
            ControlResponse response = invocation.getArgument(1);
            TransactionTemplate read = new TransactionTemplate(transactionManager);
            read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            ActionResult persisted = read.execute(status -> histories.findById(response.historyId()).orElseThrow().getStatus());
            assertEquals(response.status(), persisted);
            statuses.add(response);
            return null;
        }).when(messaging).convertAndSend(eq("/topic/status"), any(Object.class));
    }

    private Runnable scheduledTimeout() {
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Instant> deadline = ArgumentCaptor.forClass(Instant.class);
        verify(scheduler).schedule(callback.capture(), deadline.capture());
        long remaining = Duration.between(Instant.now(), deadline.getValue()).toMillis();
        assertTrue(remaining > 28000 && remaining <= 30000, "Lệnh phải chờ khoảng 30 giây");
        return callback.getValue();
    }

    @Test
    void commandStartsLoadingWithoutChangingConfirmedDeviceState() {
        doAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(ActionResult.LOADING, histories.findAll().getFirst().getStatus());
            return null;
        }).when(mqtt).publishControl(1, DeviceState.ON);

        ControlResponse response = service.control(1, DeviceState.ON, "admin");
        assertEquals(ActionResult.LOADING, response.status());
        assertEquals(DeviceState.OFF, devices.findById(1).orElseThrow().getStatus());
        assertEquals(List.of(ActionResult.LOADING), statuses.stream().map(ControlResponse::status).toList());
        assertEquals(response.historyId(), dashboard.getDashboard().pendingControls().getFirst().historyId());
        scheduledTimeout();
    }

    @Test
    void mqttAckUpdatesDeviceAndHistoryAndTimeoutCannotOverwriteIt() {
        ControlResponse response = service.control(1, DeviceState.ON, "admin");
        Runnable timeout = scheduledTimeout();
        service.receiveDeviceStatus(new DeviceStatusEvent(1, DeviceState.ON));
        timeout.run();

        assertEquals(DeviceState.ON, devices.findById(1).orElseThrow().getStatus());
        assertEquals(ActionResult.ON, histories.findById(response.historyId()).orElseThrow().getStatus());
        assertEquals(List.of(ActionResult.LOADING, ActionResult.ON), statuses.stream().map(ControlResponse::status).toList());
        assertTrue(dashboard.getDashboard().pendingControls().isEmpty());
        verify(messaging).convertAndSend(eq("/topic/devices"), any(Object.class));
    }

    @Test
    void mqttReplyDuringPublishCanFindTheAlreadyCommittedHistory() {
        doAnswer(invocation -> {
            service.receiveDeviceStatus(new DeviceStatusEvent(1, DeviceState.ON));
            return null;
        }).when(mqtt).publishControl(1, DeviceState.ON);

        ControlResponse response = service.control(1, DeviceState.ON, "admin");
        scheduledTimeout().run();
        assertEquals(ActionResult.ON, histories.findById(response.historyId()).orElseThrow().getStatus());
        assertEquals(List.of(ActionResult.LOADING, ActionResult.ON), statuses.stream().map(ControlResponse::status).toList());
    }

    @Test
    void missingAckFailsOnlyTheHistoryAndLateAckDoesNotResurrectIt() {
        ControlResponse response = service.control(1, DeviceState.ON, "admin");
        Runnable timeout = scheduledTimeout();
        timeout.run();
        timeout.run();
        assertEquals(ActionResult.FAILED, histories.findById(response.historyId()).orElseThrow().getStatus());
        assertEquals(DeviceState.OFF, devices.findById(1).orElseThrow().getStatus());
        assertEquals(List.of(ActionResult.LOADING, ActionResult.FAILED), statuses.stream().map(ControlResponse::status).toList());

        service.receiveDeviceStatus(new DeviceStatusEvent(1, DeviceState.ON));
        assertEquals(DeviceState.ON, devices.findById(1).orElseThrow().getStatus());
        assertEquals(ActionResult.FAILED, histories.findById(response.historyId()).orElseThrow().getStatus());
        assertEquals(2, statuses.size());
    }

    @Test
    void immediatePublishFailureIsSavedAndBroadcastAsFailed() {
        doThrow(new ApiException(ErrorCode.MQTT_UNAVAILABLE)).when(mqtt).publishControl(1, DeviceState.ON);
        ApiException error = assertThrows(ApiException.class, () -> service.control(1, DeviceState.ON, "admin"));
        assertEquals(ErrorCode.MQTT_UNAVAILABLE, error.getErrorCode());
        assertEquals(ActionResult.FAILED, histories.findAll().getFirst().getStatus());
        assertEquals(DeviceState.OFF, devices.findById(1).orElseThrow().getStatus());
        assertEquals(List.of(ActionResult.LOADING, ActionResult.FAILED), statuses.stream().map(ControlResponse::status).toList());
        verify(scheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void warningLedCannotBeControlledManually() {
        for (DeviceState action : DeviceState.values()) {
            ApiException error = assertThrows(ApiException.class, () -> service.control(4, action, "admin"));
            assertEquals(ErrorCode.INVALID_REQUEST, error.getErrorCode());
        }
        assertEquals(0, histories.count());
        verify(mqtt, never()).publishControl(any(), any());
    }

    @Test
    void secondCommandForSameDeviceIsRejectedWhileLoading() {
        service.control(1, DeviceState.ON, "admin");
        ApiException error = assertThrows(ApiException.class, () -> service.control(1, DeviceState.OFF, "admin"));
        assertEquals(ErrorCode.DEVICE_BUSY, error.getErrorCode());
        assertEquals(1, histories.count());
        verify(mqtt, times(1)).publishControl(eq(1), any(DeviceState.class));
        assertEquals(1, statuses.size());
    }

    @Test
    void statusForAnotherDeviceDoesNotCompleteThisCommand() {
        ControlResponse response = service.control(1, DeviceState.ON, "admin");
        service.receiveDeviceStatus(new DeviceStatusEvent(2, DeviceState.ON));
        assertEquals(ActionResult.LOADING, histories.findById(response.historyId()).orElseThrow().getStatus());
        assertEquals(DeviceState.ON, devices.findById(2).orElseThrow().getStatus());
        assertEquals(1, statuses.size());
    }

    private void makeCommandExpired(Integer historyId) {
        var history = histories.findById(historyId).orElseThrow();
        ReflectionTestUtils.setField(history, "performedAt", LocalDateTime.now().minusSeconds(35));
        histories.save(history);
    }

    @Test
    void ackAfterThirtySecondsFailsHistoryEvenIfScheduledTaskHasNotRunYet() {
        ControlResponse response = service.control(1, DeviceState.ON, "admin");
        makeCommandExpired(response.historyId());
        service.receiveDeviceStatus(new DeviceStatusEvent(1, DeviceState.ON));
        assertEquals(ActionResult.FAILED, histories.findById(response.historyId()).orElseThrow().getStatus());
        assertEquals(DeviceState.ON, devices.findById(1).orElseThrow().getStatus());
        assertEquals(List.of(ActionResult.LOADING, ActionResult.FAILED), statuses.stream().map(ControlResponse::status).toList());
    }

    @Test
    void pendingCommandsAreRescheduledAfterBackendRestart() {
        ControlResponse response = service.control(1, DeviceState.ON, "admin");
        makeCommandExpired(response.historyId());
        reset(scheduler);
        service.resumePendingControls();
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Instant> deadline = ArgumentCaptor.forClass(Instant.class);
        verify(scheduler).schedule(callback.capture(), deadline.capture());
        assertTrue(deadline.getValue().isBefore(Instant.now()));
        callback.getValue().run();
        assertEquals(ActionResult.FAILED, histories.findById(response.historyId()).orElseThrow().getStatus());
    }
}
