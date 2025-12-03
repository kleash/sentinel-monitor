package com.sentinel.platform.ingestion.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.sentinel.platform.ingestion.model.InvalidEventException;

@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(InvalidEventException.class)
    public ResponseEntity<String> handleInvalidEvent(InvalidEventException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}
