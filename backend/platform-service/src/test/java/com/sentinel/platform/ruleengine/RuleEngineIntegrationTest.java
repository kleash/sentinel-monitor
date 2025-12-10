package com.sentinel.platform.ruleengine;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.sentinel.platform.PlatformApplication;
import com.sentinel.platform.ingestion.model.NormalizedEvent;
import com.sentinel.platform.aggregation.service.AggregationService;
import com.sentinel.platform.alerting.service.AlertingService;
import com.sentinel.platform.ruleconfig.service.WorkflowService;
import com.sentinel.platform.ruleconfig.web.dto.WorkflowRequest;
import com.sentinel.platform.ruleengine.config.RuleEngineProperties;
import com.sentinel.platform.ruleengine.service.ExpectationSchedulerService;
import com.sentinel.platform.ruleengine.service.RuleEngineService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(classes = PlatformApplication.class, webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "ruleengine.scheduler-enabled=false",
        "logging.level.com.sentinel.platform=DEBUG",
        "spring.kafka.listener.auto-startup=false"
})
class RuleEngineIntegrationTest {

    @Container
    static MariaDBContainer<?> mariaDb = new MariaDBContainer<>("mariadb:10.6")
            .withDatabaseName("ruleengine")
            .withUsername("rule_engine_user")
            .withPassword("password");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        if (!mariaDb.isRunning()) {
            mariaDb.start();
        }
        registry.add("spring.datasource.url", mariaDb::getJdbcUrl);
        registry.add("spring.datasource.username", mariaDb::getUsername);
        registry.add("spring.datasource.password", mariaDb::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private ExpectationSchedulerService schedulerService;

    @Autowired
    private RuleEngineService ruleEngineService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AggregationService aggregationService;

    @Autowired
    private AlertingService alertingService;

    @Autowired
    private RuleEngineProperties ruleEngineProperties;

    @MockBean
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @org.junit.jupiter.api.BeforeEach
    void stubKafka() {
        lenient().when(kafkaTemplate.send(any(org.springframework.messaging.Message.class)))
                .thenAnswer(invocation -> {
                    org.springframework.messaging.Message<?> msg = invocation.getArgument(0);
                    String topic = (String) msg.getHeaders().get(KafkaHeaders.TOPIC);
                    Object payload = msg.getPayload();
                    String serialized = payload instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : payload.toString();
                    if (ruleEngineProperties.getRuleEvaluatedTopic().equals(topic)) {
                        aggregationService.handleRuleEvaluated(serialized);
                    } else if (ruleEngineProperties.getAlertsTriggeredTopic().equals(topic)) {
                        alertingService.handleAlertTriggered(serialized);
                    }
                    return java.util.concurrent.CompletableFuture.completedFuture(null);
                });
        lenient().when(kafkaTemplate.send(any(String.class), any(), any()))
                .thenAnswer(invocation -> {
                    String topic = invocation.getArgument(0);
                    Object payload = invocation.getArgument(2);
                    if (ruleEngineProperties.getSyntheticTopic().equals(topic)) {
                        String serialized = payload instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : payload.toString();
                        ruleEngineService.handleSyntheticMissed(serialized);
                    }
                    return java.util.concurrent.CompletableFuture.completedFuture(null);
                });
    }

    @Test
    @WithMockUser(roles = {"viewer", "operator", "config-admin"})
    void endToEndRuleEngineCreatesExpectationsAndAlertsOnMiss() throws Exception {
        createWorkflow();

        NormalizedEvent event = new NormalizedEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setSourceSystem("engine-test");
        event.setEventType("ORDER_INGEST");
        event.setEventTime(Instant.now().minusSeconds(120));
        event.setCorrelationKey("order-1");
        event.setGroup(Map.of("region", "NY"));
        event.setReceivedAt(Instant.now());
        ruleEngineService.handleNormalizedEvent(event);

        waitFor(() -> jdbcTemplate.queryForObject("select count(*) from workflow_run", Integer.class) > 0, Duration.ofSeconds(15));

        Integer expectationCount = jdbcTemplate.queryForObject("select count(*) from expectation where status = 'pending'", Integer.class);
        assertThat(expectationCount).isEqualTo(2);
        Instant dueAt = jdbcTemplate.queryForObject("select due_at from expectation limit 1", Timestamp.class).toInstant();
        System.out.println("Expectation dueAt=" + dueAt + " now=" + Instant.now());

        waitFor(() -> {
            try {
                Long total = jdbcTemplate.queryForObject("select sum(completed) from stage_aggregate where node_key = 'ingest' or node_key = 'ORDER_INGEST'", Long.class);
                return total != null && total > 0;
            } catch (Exception ex) {
                return false;
            }
        }, Duration.ofSeconds(10));

        schedulerService.pollAndEmit(10);
        String statusAfterPoll = jdbcTemplate.queryForObject("select status from expectation limit 1", String.class);
        System.out.println("Status after poll=" + statusAfterPoll);

        waitFor(() -> {
            try {
                String status = jdbcTemplate.queryForObject("select status from expectation limit 1", String.class);
                return status != null && status.equalsIgnoreCase("fired");
            } catch (Exception e) {
                return false;
            }
        }, Duration.ofSeconds(10));

        waitFor(() -> jdbcTemplate.queryForObject("select count(*) from alert", Integer.class) > 0, Duration.ofSeconds(25));
        Map<String, Object> alertRow = jdbcTemplate.queryForMap("select * from alert limit 1");
        assertThat(alertRow.get("state")).isEqualTo("open");
        assertThat(alertRow.get("severity").toString()).isNotBlank();

        String itemResponse = mockMvc.perform(get("/items/order-1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(itemResponse).contains("pendingExpectations").contains("events");

        Long alertId = ((Number) alertRow.get("id")).longValue();
        mockMvc.perform(post("/alerts/" + alertId + "/ack")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"investigating\"}"))
                .andExpect(status().isOk());

        String state = jdbcTemplate.queryForObject("select state from alert where id = ?", String.class, alertId);
        assertThat(state).isEqualTo("ack");
    }

    private void createWorkflow() {
        WorkflowRequest request = new WorkflowRequest();
        request.setName("Order Flow");
        request.setKey("order-flow");
        request.setCreatedBy("test-user");
        Map<String, Object> graph = new HashMap<>();
        graph.put("nodes", List.of(
                Map.of("key", "ingest", "eventType", "ORDER_INGEST", "start", true),
                Map.of("key", "to-system", "eventType", "ORDER_TO_SYSTEM")
        ));
        graph.put("edges", List.of(
                Map.of("from", "ingest", "to", "to-system", "maxLatencySec", 60, "severity", "red", "expectedCount", 2, "optional", false)
        ));
        request.setGraph(graph);
        workflowService.createWorkflow(request);
    }

    private void waitFor(Supplier<Boolean> condition, Duration timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (Boolean.TRUE.equals(condition.get())) {
                return;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Condition not met within timeout");
    }
}
