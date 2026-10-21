package com.iot.domain.repository;

import com.iot.domain.entity.ActionHistory;
import com.iot.common.enums.ActionResult;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;
import java.util.List;

public interface ActionHistoryRepository extends JpaRepository<ActionHistory, Integer>, JpaSpecificationExecutor<ActionHistory> {
    Optional<ActionHistory> findFirstByDeviceIdAndStatusOrderByPerformedAtDesc(Integer deviceId, ActionResult status);
    List<ActionHistory> findByStatusOrderByPerformedAtAsc(ActionResult status);
}
