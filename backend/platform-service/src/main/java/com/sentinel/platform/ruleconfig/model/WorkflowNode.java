package com.sentinel.platform.ruleconfig.model;

import jakarta.persistence.*;

@Entity
@Table(name = "workflow_node")
public class WorkflowNode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_version_id", nullable = false)
    private WorkflowVersion workflowVersion;

    @Column(name = "node_key", nullable = false, length = 100)
    private String nodeKey;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "is_start")
    private boolean start;

    @Column(name = "is_terminal")
    private boolean terminal;

    @Column(name = "ordering_policy")
    private String orderingPolicy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public WorkflowVersion getWorkflowVersion() {
        return workflowVersion;
    }

    public void setWorkflowVersion(WorkflowVersion workflowVersion) {
        this.workflowVersion = workflowVersion;
    }

    public String getNodeKey() {
        return nodeKey;
    }

    public void setNodeKey(String nodeKey) {
        this.nodeKey = nodeKey;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public boolean isStart() {
        return start;
    }

    public void setStart(boolean start) {
        this.start = start;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public void setTerminal(boolean terminal) {
        this.terminal = terminal;
    }

    public String getOrderingPolicy() {
        return orderingPolicy;
    }

    public void setOrderingPolicy(String orderingPolicy) {
        this.orderingPolicy = orderingPolicy;
    }
}
