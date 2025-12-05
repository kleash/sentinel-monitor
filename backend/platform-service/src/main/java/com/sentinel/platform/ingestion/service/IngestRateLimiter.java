package com.sentinel.platform.ingestion.service;

import java.util.concurrent.Semaphore;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.sentinel.platform.ingestion.config.IngestionProperties;

@Component
public class IngestRateLimiter {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(IngestRateLimiter.class);

    /**
     * Simple semaphore guard to shed load on REST ingest while still allowing
     * the Kafka path to continue. Keeps the service from overwhelming downstream
     * persistence under bursts.
     */
    private final Semaphore semaphore;
    private final int maxConcurrentRequests;

    public IngestRateLimiter(IngestionProperties properties) {
        this.maxConcurrentRequests = properties.getMaxConcurrentRequests();
        this.semaphore = new Semaphore(maxConcurrentRequests);
    }

    public <T> T execute(CheckedSupplier<T> supplier) {
        if (!semaphore.tryAcquire()) {
            // Prefer visibility when the REST endpoint is throttling so operators can scale.
            log.info("REST ingest throttled maxConcurrentRequests={} permitsAvailable={}",
                    maxConcurrentRequests, semaphore.availablePermits());
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "ingest overloaded");
        }
        try {
            return supplier.get();
        } finally {
            semaphore.release();
        }
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get();
    }
}
