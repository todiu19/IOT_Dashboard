package com.iot.domain.repository;

import com.iot.domain.entity.SensorData;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface SensorDataRepository extends JpaRepository<SensorData, Integer>, JpaSpecificationExecutor<SensorData> {
    Optional<SensorData> findFirstBySensorIdOrderByRecordedAtDesc(Integer sensorId);
    List<SensorData> findTop24ByOrderByRecordedAtDesc();
}
