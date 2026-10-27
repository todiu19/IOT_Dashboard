package com.iot.domain.repository;

import com.iot.common.enums.ActionResult;
import com.iot.common.enums.DeviceState;
import com.iot.domain.entity.ActionHistory;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

public interface ActionHistoryRepository extends JpaRepository<ActionHistory, Integer>, JpaSpecificationExecutor<ActionHistory> {
    Optional<ActionHistory> findFirstByDeviceIdAndStatusOrderByPerformedAtDesc(Integer deviceId, ActionResult status);
    List<ActionHistory> findByStatusOrderByPerformedAtAsc(ActionResult status);

    interface DailyCount {
        Integer getDay();
        DeviceState getState();
        Long getTotal();
    }

    @Query("""
            select day(a.performedAt) as day, a.action as state, count(a) as total
            from ActionHistory a
            where a.device.id = 4 and a.status in (com.iot.common.enums.ActionResult.ON, com.iot.common.enums.ActionResult.OFF)
              and a.performedAt >= :start and a.performedAt < :end
            group by day(a.performedAt), a.action
            """)
    List<DailyCount> countAlertsByDay(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
