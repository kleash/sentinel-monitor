package com.sentinel.platform.ruleconfig.web.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public class WorkflowRequest {
    @NotBlank
    private String name;

    @NotBlank
    private String key;

    @NotBlank
    private String createdBy;

    @NotEmpty
    private Map<String, Object> graph;

    private List<String> groupDimensions;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Map<String, Object> getGraph() {
        return graph;
    }

    public void setGraph(Map<String, Object> graph) {
        this.graph = graph;
    }

    public List<String> getGroupDimensions() {
        return groupDimensions;
    }

    public void setGroupDimensions(List<String> groupDimensions) {
        this.groupDimensions = groupDimensions;
    }
}
