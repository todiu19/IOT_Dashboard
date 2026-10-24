package com.iot.sensor.service;

import com.iot.domain.entity.Sensor;
import com.iot.domain.entity.SensorData;
import com.iot.common.dto.request.ReadingRequest;
import com.iot.common.dto.response.PageResponse;
import com.iot.common.dto.response.ReadingResponse;
import com.iot.common.exception.ApiException;
import com.iot.mqtt.SensorReadingEvent;
import com.iot.domain.repository.SensorDataRepository;
import com.iot.domain.repository.SensorRepository;
import com.iot.common.filter.TimePrefixFilter;
import jakarta.persistence.criteria.Predicate;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import com.iot.common.exception.ErrorCode;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;

@Service
public class SensorDataService {

    private final SensorDataRepository readings;
    private final SensorRepository sensors;
    private final SimpMessagingTemplate messaging;

    public SensorDataService(
            SensorDataRepository readings,
            SensorRepository sensors,
            SimpMessagingTemplate messaging
    ) {
        this.readings = readings;
        this.sensors = sensors;
        this.messaging = messaging;
    }

    @Transactional(readOnly = true)
    public PageResponse<ReadingResponse> search(
            String search,
            String sensorName,
            String time,
            int page,
            int size
    ) {
        Specification<SensorData> specification = (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (search != null && !search.isBlank()) {
                String term = search.trim();
                var sensorNameMatches = builder.like(
                        builder.lower(root.join("sensor").get("name")),
                        "%" + term.toLowerCase() + "%"
                );
                var searchMatches = new ArrayList<Predicate>();
                searchMatches.add(sensorNameMatches);
                try {
                    searchMatches.add(builder.equal(root.get("value"), new BigDecimal(term)));
                } catch (NumberFormatException ignored) {
                    // Từ khóa không phải số vẫn được tìm theo tên hoặc thời gian.
                }
                try {
                    searchMatches.add(TimePrefixFilter.matches(term, root.get("recordedAt"), builder));
                } catch (ApiException ignored) {
                    // Tìm tổng hợp không bắt buộc từ khóa phải là thời gian hợp lệ.
                }
                predicates.add(builder.or(searchMatches.toArray(Predicate[]::new)));
            }
            if (sensorName != null && !sensorName.isBlank()) {
                predicates.add(builder.equal(root.join("sensor").get("name"), sensorName));
            }
            if (time != null && !time.isBlank()) {
                predicates.add(TimePrefixFilter.matches(time, root.get("recordedAt"), builder));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                Sort.by(Sort.Direction.DESC, "recordedAt"));
        return PageResponse.from(readings.findAll(specification, pageable), SensorDataService::toResponse);
    }

    @Transactional
    public ReadingResponse create(ReadingRequest request) {
        Sensor sensor = sensors.findById(request.sensorId())
                .orElseThrow(() -> new ApiException(ErrorCode.SENSOR_NOT_FOUND, "Không tìm thấy cảm biến."));
        SensorData saved = readings.save(new SensorData(sensor, request.value(), LocalDateTime.now()));
        ReadingResponse response = toResponse(saved);
        messaging.convertAndSend("/topic/sensors", response);
        return response;
    }

    @EventListener
    public void receiveMqttReading(SensorReadingEvent event) {
        create(new ReadingRequest(event.sensorId(), event.value()));
    }

    public static ReadingResponse toResponse(SensorData reading) {
        return new ReadingResponse(
                reading.getId(),
                reading.getSensor().getId(),
                reading.getSensor().getName(),
                reading.getValue(),
                reading.getRecordedAt()
        );
    }
}
