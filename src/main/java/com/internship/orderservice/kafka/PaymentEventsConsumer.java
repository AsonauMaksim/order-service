package com.internship.orderservice.kafka;

import com.internship.orderservice.entity.Order;
import com.internship.orderservice.entity.OrderStatus;
import com.internship.orderservice.kafka.dto.PaymentEvent;
import com.internship.orderservice.kafka.dto.PaymentStatus;
import com.internship.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentEventsConsumer {

    private final OrderRepository orderRepository;

    @KafkaListener(
            topics = "${app.kafka.payments-topic}",
            containerFactory = "paymentEventKafkaListenerContainerFactory"
    )
    @Transactional
    public void onPaymentEvent(PaymentEvent event) {
        log.info("Received PaymentEvent: {}", event);

        Order order = orderRepository.findById(event.getOrderId()).orElse(null);
        if (order == null) {
            log.warn("Order not found: {}", event.getOrderId());
            return;
        }

        // простая идемпотентность
        if (event.getPaymentId() != null && event.getPaymentId().equals(order.getPaymentId())) {
            log.info("Payment already applied for order {} (paymentId={})", order.getId(), event.getPaymentId());
            return;
        }
        if (order.getStatus() == OrderStatus.PAID && event.getStatus() == PaymentStatus.SUCCESS) {
            log.info("Order {} already PAID, skip duplicate SUCCESS", order.getId());
            return;
        }

        if (event.getStatus() == PaymentStatus.SUCCESS) {
            order.setStatus(OrderStatus.PAID);
            order.setPaymentId(event.getPaymentId());
        } else if (event.getStatus() == PaymentStatus.FAILED) {
            order.setStatus(OrderStatus.PAYMENT_FAILED);
            order.setPaymentId(event.getPaymentId());
        }

        orderRepository.save(order);
        log.info("Order {} updated: status={}, paymentId={}", order.getId(), order.getStatus(), order.getPaymentId());
    }
}

