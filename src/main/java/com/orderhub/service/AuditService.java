package com.orderhub.service;

import com.orderhub.domain.AuditEvent;
import com.orderhub.domain.Order;
import com.orderhub.dto.AuditEventResponse;
import com.orderhub.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    /**
     * Records an audit event in the caller's transaction, so the event is stored if and only if the change it describes is.
     * A failure to write it fails the change as well.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String entityType, Long entityId, String action, String actor, Object subject) {
        auditEventRepository.save(new AuditEvent(entityType, entityId, action, actor, describe(subject)));
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> historyFor(String entityType, Long entityId) {
        return auditEventRepository.findByEntityTypeAndEntityIdOrderByCreatedAtAsc(entityType, entityId).stream()
                .map(event -> new AuditEventResponse(event.getAction(), event.getActor(), event.getDetails(), event.getCreatedAt()))
                .toList();
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
