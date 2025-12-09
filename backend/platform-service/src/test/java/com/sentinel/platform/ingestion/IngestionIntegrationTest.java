package com.sentinel.platform.ingestion;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.sentinel.platform.PlatformApplication;
import com.sentinel.platform.ingestion.model.RawEventRequest;
import com.sentinel.platform.ingestion.service.IngestionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(classes = PlatformApplication.class, webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "logging.level.com.sentinel.platform=DEBUG",
        "ruleengine.scheduler-enabled=false",
        "spring.kafka.listener.auto-startup=false"
})
class IngestionIntegrationTest {

    @Container
    static MariaDBContainer<?> mariaDb = new MariaDBContainer<>("mariadb:10.6")
            .withDatabaseName("ingestion")
            .withUsername("ingestion_user")
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
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IngestionService ingestionService;

    @MockBean
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @BeforeEach
    void resetKafkaTemplate() {
        Mockito.reset(kafkaTemplate);
        when(kafkaTemplate.send(any(Message.class))).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    @WithMockUser(roles = {"operator"})
    void restIngestStoresAndPublishes() throws Exception {
        Map<String, Object> payload = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "sourceSystem", "rest-client",
                "eventType", "TEST_EVENT",
                "eventTime", Instant.now().toString(),
                "correlationKey", "corr-1",
                "group", Map.of("region", "NY")
        );

        mockMvc.perform(post("/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        Integer storedCount = jdbcTemplate.queryForObject(
                "select count(*) from event_raw where correlation_key = ?", Integer.class, "corr-1");
        assertThat(storedCount).isEqualTo(1);
        var captor = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(kafkaTemplate, times(1)).send(captor.capture());
        Message<?> sentMessage = captor.getValue();
        assertThat(sentMessage.getHeaders().get(KafkaHeaders.TOPIC)).isEqualTo("events.normalized");

        mockMvc.perform(post("/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
        Integer storedCountAfterDup = jdbcTemplate.queryForObject(
                "select count(*) from event_raw where correlation_key = ?", Integer.class, "corr-1");
        assertThat(storedCountAfterDup).isEqualTo(1);
    }

    @Test
    void kafkaIngestWritesAndPublishes() {
        RawEventRequest request = new RawEventRequest();
        request.setEventId(UUID.randomUUID().toString());
        request.setSourceSystem("kafka-source");
        request.setEventType("TRADE_INGEST");
        request.setEventTime(Instant.now());
        request.setCorrelationKey("trade-123");
        request.setGroup(Map.of("book", "EQD"));

        ingestionService.ingestFromKafka(request, Instant.now(), Map.of());

        Integer storedCount = jdbcTemplate.queryForObject(
                "select count(*) from event_raw where correlation_key = ?", Integer.class, "trade-123");
        assertThat(storedCount).isEqualTo(1);
        var captor = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(kafkaTemplate, times(1)).send(captor.capture());
        assertThat(captor.getValue().getHeaders().get(KafkaHeaders.TOPIC)).isEqualTo("events.normalized");
    }

    @Test
    void invalidKafkaEventGoesToDlq() {
        RawEventRequest request = new RawEventRequest();
        request.setSourceSystem("kafka-source");
        request.setEventTime(Instant.now());
        request.setCorrelationKey("trade-999");

        ingestionService.ingestFromKafka(request, Instant.now(), Map.of());

        var captor = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(kafkaTemplate, times(1)).send(captor.capture());
        assertThat(captor.getValue().getHeaders().get(KafkaHeaders.TOPIC)).isEqualTo("events.dlq");
    }
}
