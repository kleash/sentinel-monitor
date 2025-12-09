package com.sentinel.platform.alerting.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sentinel.platform.alerting.model.Alert;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    Optional<Alert> findFirstByDedupeKey(String dedupeKey);

    List<Alert> findByStateOrderByLastTriggeredAtDesc(String state, Pageable pageable);

    List<Alert> findAllByOrderByLastTriggeredAtDesc(Pageable pageable);

    List<Alert> findByCorrelationKeyAndWorkflowVersionIdOrderByLastTriggeredAtDesc(String correlationKey, Long workflowVersionId);
}
