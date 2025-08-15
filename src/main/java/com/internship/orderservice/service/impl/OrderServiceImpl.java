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
import com.internship.orderservice.mapper.OrderItemMapper;
import com.internship.orderservice.mapper.OrderMapper;
import com.internship.orderservice.repository.ItemRepository;
import com.internship.orderservice.repository.OrderRepository;
import com.internship.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final ItemRepository itemRepository;
    private final UserClient userClient;

    private static OrderStatus parseStatus(String s) {
        try {
            return OrderStatus.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid status: " + s);
        }
    }

    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {

        Order order = orderMapper.toEntity(request);
        order.setStatus(parseStatus(request.getStatus()));
        order.setCreationDate(LocalDateTime.now());

        List<OrderItem> orderItems = request.getItems().stream().map(itemRequest -> {
            Item item = itemRepository.findById(itemRequest.getItemId())
                    .orElseThrow(() -> new NotFoundException("Item not found with id: " + itemRequest.getItemId()));
            return OrderItem.builder()
                    .item(item)
                    .quantity(itemRequest.getQuantity())
                    .order(order)
                    .build();
        }).toList();

        order.setOrderItems(orderItems);
        Order savedOrder = orderRepository.save(order);

        UserResponse user = userClient.getByUserId(savedOrder.getUserId());
        return orderMapper.toDto(savedOrder, user);
    }

    @Override
    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found with id: " + id));

        UserResponse user = userClient.getByUserId(order.getUserId());
        return orderMapper.toDto(order, user);
    }

    @Override
    public List<OrderResponse> getOrdersByIds(List<Long> ids) {
        List<Order> orders = orderRepository.findByIdIn(ids);
        return orders.stream().map(order -> {
            UserResponse user = userClient.getByUserId(order.getUserId());
            return orderMapper.toDto(order, user);
        }).toList();
    }

    @Override
    public List<OrderResponse> getOrdersByStatuses(List<OrderStatus> statuses) {
        List<Order> orders = orderRepository.findByStatusIn(statuses);
        return orders.stream().map(order -> {
            UserResponse user = userClient.getByUserId(order.getUserId());
            return orderMapper.toDto(order, user);
        }).toList();
    }

    @Override
    @Transactional
    public OrderResponse updateOrder(Long id, OrderRequest request) {

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found with id: " + id));

//        order.setStatus(OrderStatus.valueOf(request.getStatus()));
        order.setStatus(parseStatus(request.getStatus()));

        order.getOrderItems().clear();
        List<OrderItem> updatedItems = request.getItems().stream().map(itemRequest -> {
            Item item = itemRepository.findById(itemRequest.getItemId())
                    .orElseThrow(() -> new NotFoundException("Item not found with id: " + itemRequest.getItemId()));
            return OrderItem.builder()
                    .item(item)
                    .quantity(itemRequest.getQuantity())
                    .order(order)
                    .build();
        }).toList();

        order.getOrderItems().addAll(updatedItems);
        Order saved = orderRepository.save(order);

        UserResponse user = userClient.getByUserId(saved.getUserId());
        return orderMapper.toDto(saved, user);
    }

    @Override
    @Transactional
    public void deleteOrder(Long id) {
        if (!orderRepository.existsById(id)) {
            throw new NotFoundException("Order not found with id: " + id);
        }
        orderRepository.deleteById(id);
    }
}
