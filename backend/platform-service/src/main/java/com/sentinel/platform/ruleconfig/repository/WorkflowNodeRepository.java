package com.sentinel.platform.ruleconfig.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sentinel.platform.ruleconfig.model.WorkflowNode;

public interface WorkflowNodeRepository extends JpaRepository<WorkflowNode, Long> {

    @Query("select wn from WorkflowNode wn join fetch wn.workflowVersion v join v.workflow w where wn.eventType = :eventType and w.activeVersionId = v.id")
    List<WorkflowNode> findActiveByEventType(@Param("eventType") String eventType);
}
