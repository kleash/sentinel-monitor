package com.sentinel.platform.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Permissive security configuration used for local demos and tests.
 * When enabled, all endpoints are open and JWT enforcement is disabled.
 */
@Configuration
@ConditionalOnProperty(name = "security.disable-auth", havingValue = "true")
public class NoAuthSecurityConfig {

    @Bean
    public SecurityFilterChain permitAllFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .oauth2ResourceServer(oauth -> {
                    oauth.disable();
                });
        return http.build();
    }
}
