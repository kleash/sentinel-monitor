package com.sentinel.platform.ruleconfig.model;

import jakarta.persistence.*;

@Entity
@Table(name = "workflow_edge")
public class WorkflowEdge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_node_id", nullable = false)
    private WorkflowNode fromNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_node_id", nullable = false)
    private WorkflowNode toNode;

    @Column(name = "max_latency_sec")
    private Integer maxLatencySec;

    @Column(name = "absolute_deadline")
    private String absoluteDeadline;

    @Column(name = "optional")
    private boolean optional;

    private String severity;

    @Column(name = "expected_count")
    private Integer expectedCount;

    public Long getId() {
        return id;
    }

    public WorkflowNode getFromNode() {
        return fromNode;
    }

    public void setFromNode(WorkflowNode fromNode) {
        this.fromNode = fromNode;
    }

    public WorkflowNode getToNode() {
        return toNode;
    }

    public void setToNode(WorkflowNode toNode) {
        this.toNode = toNode;
    }

    public Integer getMaxLatencySec() {
        return maxLatencySec;
    }

    public void setMaxLatencySec(Integer maxLatencySec) {
        this.maxLatencySec = maxLatencySec;
    }

    public String getAbsoluteDeadline() {
        return absoluteDeadline;
    }

    public void setAbsoluteDeadline(String absoluteDeadline) {
        this.absoluteDeadline = absoluteDeadline;
    }

    public boolean isOptional() {
        return optional;
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public Integer getExpectedCount() {
        return expectedCount;
    }

    public void setExpectedCount(Integer expectedCount) {
        this.expectedCount = expectedCount;
    }
}
