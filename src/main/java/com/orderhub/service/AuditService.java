package com.orderhub.service;

import com.orderhub.domain.AuditEvent;
import com.orderhub.domain.Order;
import com.orderhub.dto.AuditEventResponse;
import com.orderhub.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

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
