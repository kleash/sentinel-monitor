package com.sentinel.platform.ruleconfig.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sentinel.platform.ruleconfig.model.WorkflowVersion;

public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersion, Long> {
    List<WorkflowVersion> findByWorkflowIdOrderByVersionNumDesc(Long workflowId);

    Optional<WorkflowVersion> findFirstByWorkflowIdOrderByVersionNumDesc(Long workflowId);
}
