package com.sentinel.platform.ingestion.web;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sentinel.platform.ingestion.model.NormalizedEvent;
import com.sentinel.platform.ingestion.model.RawEventRequest;
import com.sentinel.platform.ingestion.service.IngestRateLimiter;
import com.sentinel.platform.ingestion.service.IngestionService;

@Validated
@RestController
@RequestMapping("/ingest")
public class IngestController {
    private static final Logger log = LoggerFactory.getLogger(IngestController.class);

    private final IngestionService ingestionService;
    private final IngestRateLimiter rateLimiter;

    public IngestController(IngestionService ingestionService, IngestRateLimiter rateLimiter) {
        this.ingestionService = ingestionService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    @PreAuthorize("hasRole('operator') or hasRole('config-admin')")
    public ResponseEntity<NormalizedEvent> ingest(@Valid @RequestBody RawEventRequest request,
                                                  @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        if (!StringUtils.hasText(request.getEventId()) && StringUtils.hasText(idempotencyKey)) {
            request.setEventId(idempotencyKey);
        }
        NormalizedEvent normalized = rateLimiter.execute(() -> ingestionService.ingestFromRest(request));
        log.debug("REST ingest accepted correlationKey={} eventType={}", normalized.getCorrelationKey(), normalized.getEventType());
        return ResponseEntity.ok(normalized);
    }
}
