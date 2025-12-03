package com.sentinel.platform.ruleconfig.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sentinel.platform.ruleconfig.model.Workflow;

public interface WorkflowRepository extends JpaRepository<Workflow, Long> {
    Optional<Workflow> findByKey(String key);

    boolean existsByKey(String key);
}
