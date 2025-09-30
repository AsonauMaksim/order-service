package com.internship.orderservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.kafka")
public class KafkaTopicsProperties {

    private String ordersTopic;
    private String paymentsTopic;
    private String consumerGroup;
}
