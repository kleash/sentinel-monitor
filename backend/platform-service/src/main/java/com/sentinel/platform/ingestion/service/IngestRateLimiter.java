package com.sentinel.platform.ingestion.service;

import java.util.concurrent.Semaphore;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.sentinel.platform.ingestion.config.IngestionProperties;

@Component
public class IngestRateLimiter {
    private final Semaphore semaphore;

    public IngestRateLimiter(IngestionProperties properties) {
        this.semaphore = new Semaphore(properties.getMaxConcurrentRequests());
    }

    public <T> T execute(CheckedSupplier<T> supplier) {
        if (!semaphore.tryAcquire()) {
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
