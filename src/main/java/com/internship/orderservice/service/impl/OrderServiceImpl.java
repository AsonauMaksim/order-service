package com.internship.orderservice.service.impl;

import com.internship.orderservice.client.UserClient;
import com.internship.orderservice.dto.external.UserResponse;
import com.internship.orderservice.dto.request.OrderRequest;
import com.internship.orderservice.dto.response.OrderResponse;
import com.internship.orderservice.entity.Item;
import com.internship.orderservice.entity.Order;
import com.internship.orderservice.entity.OrderItem;
import com.internship.orderservice.entity.OrderStatus;
import com.internship.orderservice.exception.NotFoundException;
import com.internship.orderservice.kafka.OrderEventsProducer;
import com.internship.orderservice.kafka.dto.OrderEvent;
import com.internship.orderservice.mapper.OrderMapper;
import com.internship.orderservice.repository.ItemRepository;
import com.internship.orderservice.repository.OrderRepository;
import com.internship.orderservice.service.OrderService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final ItemRepository itemRepository;
    private final UserClient userClient;
    private final OrderEventsProducer orderEventsProducer;

    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {

        Long credentialsId = request.getUserId();
        if (credentialsId == null) {
            throw new NotFoundException("Missing user credentials id");
        }

        Long actualUserId = resolveActualUserId(credentialsId);
        request.setUserId(actualUserId);

        UserResponse user = requireUser(actualUserId);

        Order order = orderMapper.toEntity(request);

        order.setStatus(OrderStatus.PENDING);
        order.setCreationDate(LocalDateTime.now());

        order.setPaymentId(null);

        List<OrderItem> orderItems = request.getItems().stream()
                .map(itemReq -> {
                    Item item = itemRepository.findById(itemReq.getItemId())
                            .orElseThrow(() ->
                                    new NotFoundException("Item not found with id: " + itemReq.getItemId()));
                    return OrderItem.builder()
                            .order(order)
                            .item(item)
                            .quantity(itemReq.getQuantity())
                            .build();
                })
                .toList();

        order.setOrderItems(orderItems);

        Order saved = orderRepository.save(order);

        BigDecimal paymentAmount = orderItems.stream()
                .map(orderItem -> orderItem.getItem().getPrice()
                        .multiply(BigDecimal.valueOf(orderItem.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        OrderEvent event = OrderEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .orderId(saved.getId())
                .userId(saved.getUserId())
                .paymentAmount(paymentAmount)
                .build();

        orderEventsProducer.send(event);

        return orderMapper.toDto(saved, user);
    }

    @Override
    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found with id: " + id));

        UserResponse user = safeGetUser(order.getUserId());
        return orderMapper.toDto(order, user);
    }

    @Override
    public List<OrderResponse> getOrdersByIds(List<Long> ids) {
        return orderRepository.findByIdIn(ids).stream()
                .map(o -> orderMapper.toDto(o, safeGetUser(o.getUserId())))
                .toList();
    }

    @Override
    public List<OrderResponse> getOrdersByStatuses(List<OrderStatus> statuses) {
        return orderRepository.findByStatusIn(statuses).stream()
                .map(o -> orderMapper.toDto(o, safeGetUser(o.getUserId())))
                .toList();
    }

    @Override
    @Transactional
    public OrderResponse updateOrder(Long id, OrderRequest request, Long credentialsId) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found with id: " + id));

        if (credentialsId == null) {
            throw new AccessDeniedException("Missing X-User-Id");
        }
        Long actualUserId = resolveActualUserId(credentialsId);

        if (!order.getUserId().equals(actualUserId)) {
            throw new AccessDeniedException("Access denied: you can update only your orders");
        }


        String requestedStatusStr = request.getStatus();
        if (requestedStatusStr != null && !requestedStatusStr.isBlank()) {
            OrderStatus requested = parseStatus(requestedStatusStr);

            EnumSet<OrderStatus> paymentStatuses = EnumSet.of(OrderStatus.PAID, OrderStatus.PAYMENT_FAILED);

            if (paymentStatuses.contains(requested)) {
                throw new AccessDeniedException("You cannot set payment statuses manually");
            }

            order.setStatus(requested);
        }

        order.getOrderItems().clear();
        List<OrderItem> updatedItems = request.getItems().stream()
                .map(itemRequest -> {
                    Item item = itemRepository.findById(itemRequest.getItemId())
                            .orElseThrow(() ->
                                    new NotFoundException("Item not found with id: " + itemRequest.getItemId()));
                    return OrderItem.builder()
                            .order(order)
                            .item(item)
                            .quantity(itemRequest.getQuantity())
                            .build();
                })
                .toList();
        order.getOrderItems().addAll(updatedItems);

        Order saved = orderRepository.save(order);
        UserResponse user = safeGetUser(saved.getUserId());
        return orderMapper.toDto(saved, user);
    }

    @Override
    @Transactional
    public void deleteOrder(Long id, Long credentialsId) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found with id: " + id));

        if (credentialsId == null) {
            throw new AccessDeniedException("Missing X-User-Id");
        }
        Long actualUserId = resolveActualUserId(credentialsId);

        if (!order.getUserId().equals(actualUserId)) {
            throw new AccessDeniedException("Access denied: you can delete only your orders");
        }

        orderRepository.delete(order);
    }

    private static OrderStatus parseStatus(String s) {
        try {
            return OrderStatus.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid status: " + s);
        }
    }

    private Long resolveActualUserId(Long credentialsId) {
        try {
            UserResponse user = userClient.getByCredentialsId(credentialsId);
            if (user == null || user.getId() == null) {
                throw new NotFoundException("User does not exist: " + credentialsId);
            }
            return user.getId();
        } catch (NotFoundException | FeignException.NotFound e) {
            throw new NotFoundException("User does not exist: " + credentialsId);
        }
    }

    private UserResponse requireUser(Long userId) {
        try {
            return userClient.getByUserId(userId);
        } catch (NotFoundException e) {
            throw new NotFoundException("User does not exist: " + userId);
        }
    }

    private UserResponse safeGetUser(Long userId) {
        try {
            return userClient.getByUserId(userId);
        } catch (NotFoundException e) {
            return null;
        }
    }
}

