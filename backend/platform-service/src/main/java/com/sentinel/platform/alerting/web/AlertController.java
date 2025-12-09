package com.sentinel.platform.alerting.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.sentinel.platform.alerting.model.Alert;
import com.sentinel.platform.alerting.service.AlertingService;

@RestController
public class AlertController {

    private final AlertingService alertingService;

    public AlertController(AlertingService alertingService) {
        this.alertingService = alertingService;
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasRole('viewer') or hasRole('operator') or hasRole('config-admin')")
    public List<Alert> list(@RequestParam(value = "state", required = false) String state,
                            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        return alertingService.list(state, limit);
    }

    @PostMapping("/alerts/{id}/ack")
    @PreAuthorize("hasRole('operator') or hasRole('config-admin')")
    public void ack(@PathVariable long id, Authentication authentication, @RequestBody(required = false) Map<String, Object> body) {
        String reason = body != null ? (String) body.getOrDefault("reason", "ack") : "ack";
        String actor = authentication != null ? authentication.getName() : "system";
        if (!alertingService.ack(id, actor, reason)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found");
        }
    }

    @PostMapping("/alerts/{id}/suppress")
    @PreAuthorize("hasRole('operator') or hasRole('config-admin')")
    public void suppress(@PathVariable long id, Authentication authentication, @RequestBody(required = false) Map<String, Object> body) {
        String reason = body != null ? (String) body.getOrDefault("reason", "suppressed") : "suppressed";
        String actor = authentication != null ? authentication.getName() : "system";
        Instant until = null;
        if (body != null && body.get("until") != null) {
            until = Instant.parse(body.get("until").toString());
        }
        if (!alertingService.suppress(id, actor, reason, until)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found");
        }
    }

    @PostMapping("/alerts/{id}/resolve")
    @PreAuthorize("hasRole('operator') or hasRole('config-admin')")
    public void resolve(@PathVariable long id, Authentication authentication, @RequestBody(required = false) Map<String, Object> body) {
        String reason = body != null ? (String) body.getOrDefault("reason", "resolved") : "resolved";
        String actor = authentication != null ? authentication.getName() : "system";
        if (!alertingService.resolve(id, actor, reason)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found");
        }
    }
}
