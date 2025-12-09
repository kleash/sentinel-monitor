package com.sentinel.platform.alerting.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sentinel.platform.alerting.model.AuditLogEntry;

public interface AuditRepository extends JpaRepository<AuditLogEntry, Long> {
}
