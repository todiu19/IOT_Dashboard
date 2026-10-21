package com.iot.history.service;

import com.iot.domain.entity.ActionHistory;
import com.iot.common.enums.DeviceState;
import com.iot.common.enums.ActionResult;
import com.iot.common.dto.response.ActionHistoryResponse;
import com.iot.common.dto.response.PageResponse;
import com.iot.domain.repository.ActionHistoryRepository;
import com.iot.common.filter.TimePrefixFilter;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

@Service
public class ActionHistoryService {

    private final ActionHistoryRepository histories;

    public ActionHistoryService(ActionHistoryRepository histories) {
        this.histories = histories;
    }

    @Transactional(readOnly = true)
    public PageResponse<ActionHistoryResponse> search(
            Integer deviceId,
            DeviceState action,
            ActionResult status,
            String time,
            int page,
            int size
    ) {
        Specification<ActionHistory> specification = (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (deviceId != null) predicates.add(builder.equal(root.get("device").get("id"), deviceId));
            if (action != null) predicates.add(builder.equal(root.get("action"), action));
            if (status != null) predicates.add(builder.equal(root.get("status"), status));
            if (time != null && !time.isBlank()) {
                predicates.add(TimePrefixFilter.matches(time, root.get("performedAt"), builder));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                Sort.by(Sort.Direction.DESC, "performedAt"));
        return PageResponse.from(histories.findAll(specification, pageable), ActionHistoryService::toResponse);
    }

    public static ActionHistoryResponse toResponse(ActionHistory history) {
        return new ActionHistoryResponse(
                history.getId(),
                history.getDevice().getId(),
                history.getDevice().getName(),
                history.getUser().getId(),
                history.getUser().getUsername(),
                history.getAction(),
                history.getStatus(),
                history.getPerformedAt()
        );
    }
}
