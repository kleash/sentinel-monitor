package com.sentinel.platform.ingestion.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.sentinel.platform.ingestion.model.RawEventEntity;
import com.sentinel.platform.ingestion.model.RawEventRecord;
import com.sentinel.platform.ingestion.model.SaveResult;

@Repository
public class EventRawRepository {
    private static final Logger log = LoggerFactory.getLogger(EventRawRepository.class);

    private final RawEventJpaRepository rawEventJpaRepository;
    private final ObjectMapper objectMapper;

    public EventRawRepository(RawEventJpaRepository rawEventJpaRepository, ObjectMapper objectMapper) {
        this.rawEventJpaRepository = rawEventJpaRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SaveResult save(RawEventRecord record) {
        if (rawEventJpaRepository.findBySourceSystemAndSourceEventId(record.getSourceSystem(), record.getEventId()).isPresent()) {
            log.debug("Duplicate raw event ignored sourceSystem={} eventId={}", record.getSourceSystem(), record.getEventId());
            return SaveResult.DUPLICATE;
        }
        try {
            RawEventEntity entity = new RawEventEntity();
            entity.setSourceEventId(record.getEventId());
            entity.setSourceSystem(record.getSourceSystem());
            entity.setEventType(record.getEventType());
            entity.setWorkflowKey(record.getWorkflowKey());
            entity.setCorrelationKey(record.getCorrelationKey());
            entity.setGroupDims(toJson(record.getGroup()));
            entity.setEventTimeUtc(record.getEventTimeUtc());
            entity.setReceivedAt(record.getReceivedAt());
            entity.setPayload(toJson(record.getPayload()));
            entity.setIngestStatus(record.getIngestStatus());
            rawEventJpaRepository.save(entity);
            return SaveResult.INSERTED;
        } catch (DataIntegrityViolationException ex) {
            log.debug("Duplicate raw event ignored sourceSystem={} eventId={}", record.getSourceSystem(), record.getEventId());
            return SaveResult.DUPLICATE;
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize JSON field", e);
        }
    }
}
