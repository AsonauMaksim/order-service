package com.internship.orderservice.integration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
public abstract class BaseIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("order_db_test")
                    .withUsername("postgres")
                    .withPassword("12345");

    protected static WireMockServer WIREMOCK;

    @BeforeAll
    static void startInfra() {
        if (!POSTGRES.isRunning()) POSTGRES.start();
        WIREMOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        WIREMOCK.start();
    }

    @AfterAll
    static void stopInfra() {
        if (WIREMOCK != null) WIREMOCK.stop();
        if (POSTGRES != null) POSTGRES.stop();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("external.user-service.url", () -> WIREMOCK.baseUrl());
    }
}
