package com.orderhub.service;

import com.orderhub.domain.AuditEvent;
import com.orderhub.domain.Order;
import com.orderhub.dto.AuditEventResponse;
import com.orderhub.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    /**
     * Records an audit event without slowing down the calling request.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String entityType, Long entityId, String action, String actor, Object subject) {
        try {
            String details = describe(subject);
            auditEventRepository.save(new AuditEvent(entityType, entityId, action, actor, details));
        } catch (Exception ex) {
            log.warn("Could not write audit event {} for {} {}: {}", action, entityType, entityId, ex.getMessage());
        }
    }

    public List<AuditEventResponse> historyFor(String entityType, Long entityId) {
        return List.of();
    }

    private String describe(Object subject) {
        if (subject instanceof Order order) {
            String skus = order.getItems().stream()
                    .map(item -> item.getProduct().getSku() + "x" + item.getQuantity())
                    .collect(Collectors.joining(","));
            return "status=" + order.getStatus() + ", total=" + order.getTotal() + ", items=[" + skus + "]";
        }
        return String.valueOf(subject);
    }
}
