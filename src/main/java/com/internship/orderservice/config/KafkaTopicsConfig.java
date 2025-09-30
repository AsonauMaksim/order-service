package com.internship.orderservice.config;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration
@RequiredArgsConstructor
public class KafkaTopicsConfig {

    private final KafkaTopicsProperties topics;

    @Bean
    public KafkaAdmin.NewTopics appTopics() {
        NewTopic orders = TopicBuilder
                .name(topics.getOrdersTopic())
                .partitions(1)
                .replicas(1)
                .build();

        NewTopic payments = TopicBuilder
                .name(topics.getPaymentsTopic())
                .partitions(1)
                .replicas(1)
                .build();

        return new KafkaAdmin.NewTopics(orders, payments);
    }
}
