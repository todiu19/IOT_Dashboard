package com.iot.dashboard.service;

import com.iot.common.dto.response.DashboardResponse;
import com.iot.common.enums.ActionResult;
import com.iot.domain.repository.ActionHistoryRepository;
import com.iot.domain.repository.DeviceRepository;
import com.iot.device.service.DeviceService;
import com.iot.domain.repository.SensorDataRepository;
import com.iot.domain.repository.SensorRepository;
import com.iot.sensor.service.SensorDataService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final SensorRepository sensors;
    private final SensorDataRepository readings;
    private final DeviceRepository devices;
    private final ActionHistoryRepository histories;

    public DashboardService(
            SensorRepository sensors,
            SensorDataRepository readings,
            DeviceRepository devices,
            ActionHistoryRepository histories
    ) {
        this.sensors = sensors;
        this.readings = readings;
        this.devices = devices;
        this.histories = histories;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        var latest = sensors.findAll(Sort.by("id")).stream()
                .map(sensor -> readings.findFirstBySensorIdOrderByRecordedAtDesc(sensor.getId()))
                .flatMap(java.util.Optional::stream)
                .map(SensorDataService::toResponse)
                .toList();
        var chart = readings.findTop24ByOrderByRecordedAtDesc().reversed().stream()
                .map(SensorDataService::toResponse)
                .toList();
        var deviceList = devices.findAll(Sort.by("id")).stream()
                .map(DeviceService::toResponse)
                .toList();
        var pending = histories.findByStatusOrderByPerformedAtAsc(ActionResult.LOADING).stream()
                .map(DeviceService::toControlResponse).toList();
        return new DashboardResponse(latest, chart, deviceList, pending);
    }
}
