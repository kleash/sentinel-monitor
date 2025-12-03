package com.sentinel.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.sentinel.platform.ingestion.config.IngestionProperties;
import com.sentinel.platform.ruleengine.config.RuleEngineProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({IngestionProperties.class, RuleEngineProperties.class})
public class PlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}
