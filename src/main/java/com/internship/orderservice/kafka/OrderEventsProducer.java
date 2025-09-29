package com.internship.orderservice.kafka;

import com.internship.orderservice.config.KafkaTopicsProperties;
import com.internship.orderservice.kafka.dto.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OrderEventsProducer {

    private final KafkaTemplate<String, OrderEvent> orderEventKafkaTemplate;
    private final KafkaTopicsProperties topics;

    public void send(OrderEvent event) {
        String key = event.getOrderId() == null ? null : String.valueOf(event.getOrderId());

        orderEventKafkaTemplate
                .send(topics.getOrdersTopic(), key, event)
                .whenComplete((res, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send OrderEvent {}: {}", event, ex.getMessage(), ex);
                    } else if (res != null && res.getRecordMetadata() != null) {
                        log.info("OrderEvent sent: topic={}, partition={}, offset={}, key={}",
                                res.getRecordMetadata().topic(),
                                res.getRecordMetadata().partition(),
                                res.getRecordMetadata().offset(),
                                key);
                    } else {
                        log.info("OrderEvent sent (no metadata available), key={}", key);
                    }
                });
    }
}

