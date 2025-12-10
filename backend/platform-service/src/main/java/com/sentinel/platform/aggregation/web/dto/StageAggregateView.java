package com.sentinel.platform.aggregation.web.dto;

import java.time.Instant;

import com.sentinel.platform.aggregation.model.StageAggregate;

public record StageAggregateView(
        Long workflowVersionId,
        String groupHash,
        String nodeKey,
        Instant bucketStart,
        int inFlight,
        int completed,
        int late,
        int failed
) {
    public static StageAggregateView from(StageAggregate aggregate) {
        return new StageAggregateView(
                aggregate.getWorkflowVersionId(),
                aggregate.getGroupDimHash(),
                aggregate.getNodeKey(),
                aggregate.getBucketStart(),
                aggregate.getInFlight(),
                aggregate.getCompleted(),
                aggregate.getLate(),
                aggregate.getFailed()
        );
    }
}
