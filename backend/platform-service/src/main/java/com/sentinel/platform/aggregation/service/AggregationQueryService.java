package com.sentinel.platform.aggregation.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.sentinel.platform.aggregation.model.StageAggregate;
import com.sentinel.platform.aggregation.repository.StageAggregateRepository;

@Service
public class AggregationQueryService {
    /**
     * Read-only service for surfacing aggregate snapshots to REST controllers.
     */
    private final StageAggregateRepository repository;

    public AggregationQueryService(StageAggregateRepository repository) {
        this.repository = repository;
    }

    public List<StageAggregate> findAggregates(Long workflowVersionId, int limit, String groupHash) {
        PageRequest page = PageRequest.of(0, limit);
        if (groupHash != null) {
            return repository.findByWorkflowVersionIdAndGroupDimHashOrderByBucketStartDesc(workflowVersionId, groupHash, page);
        }
        return repository.findByWorkflowVersionIdOrderByBucketStartDesc(workflowVersionId, page);
    }

    public List<StageAggregate> wallboard(int limit) {
        return repository.findAllByOrderByBucketStartDesc(PageRequest.of(0, limit));
    }
}
