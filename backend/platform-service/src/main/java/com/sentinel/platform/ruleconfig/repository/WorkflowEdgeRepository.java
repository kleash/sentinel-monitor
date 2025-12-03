package com.sentinel.platform.ruleconfig.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sentinel.platform.ruleconfig.model.WorkflowEdge;

public interface WorkflowEdgeRepository extends JpaRepository<WorkflowEdge, Long> {
}
