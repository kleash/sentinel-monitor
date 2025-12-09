package com.sentinel.platform.ingestion.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sentinel.platform.ingestion.model.RawEventEntity;

public interface RawEventJpaRepository extends JpaRepository<RawEventEntity, Long> {
    Optional<RawEventEntity> findBySourceSystemAndSourceEventId(String sourceSystem, String sourceEventId);
}
