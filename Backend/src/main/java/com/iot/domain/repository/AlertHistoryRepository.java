package com.iot.domain.repository;

import com.iot.common.enums.DeviceState;
import com.iot.domain.entity.AlertHistory;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertHistoryRepository extends JpaRepository<AlertHistory, Integer> {
    interface DailyCount {
        Integer getDay();
        DeviceState getState();
        Long getTotal();
    }

    @Query("""
            select day(h.occurredAt) as day, h.state as state, count(h) as total
            from AlertHistory h where h.occurredAt >= :start and h.occurredAt < :end
            group by day(h.occurredAt), h.state
            """)
    List<DailyCount> countByDay(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
