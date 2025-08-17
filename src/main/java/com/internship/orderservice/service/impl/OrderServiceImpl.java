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

    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {

        UserResponse user = requireUser(request.getUserId());

        Order order = orderMapper.toEntity(request);
        order.setStatus(parseStatus(request.getStatus()));
        order.setCreationDate(LocalDateTime.now());

        List<OrderItem> orderItems = request.getItems().stream()
                .map(itemReq -> {
                    Item item = itemRepository.findById(itemReq.getItemId())
                            .orElseThrow(() -> new NotFoundException("Item not found with id: " + itemReq.getItemId()));
                    return OrderItem.builder()
                            .order(order)
                            .item(item)
                            .quantity(itemReq.getQuantity())
                            .build();
                })
                .toList();

        order.setOrderItems(orderItems);

        Order saved = orderRepository.save(order);
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
    public OrderResponse updateOrder(Long id, OrderRequest request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found with id: " + id));

        order.setStatus(parseStatus(request.getStatus()));

        order.getOrderItems().clear();
        List<OrderItem> updatedItems = request.getItems().stream()
                .map(itemReq -> {
                    Item item = itemRepository.findById(itemReq.getItemId())
                            .orElseThrow(() -> new NotFoundException("Item not found with id: " + itemReq.getItemId()));
                    return OrderItem.builder()
                            .order(order)
                            .item(item)
                            .quantity(itemReq.getQuantity())
                            .build();
                })
                .toList();
        order.getOrderItems().addAll(updatedItems);

        Order saved = orderRepository.save(order);
        UserResponse user = safeGetUser(saved.getUserId()); // может быть null
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

    private static OrderStatus parseStatus(String s) {
        try {
            return OrderStatus.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid status: " + s);
        }
    }

    private UserResponse requireUser(Long userId) {
        try {
            return userClient.getByUserId(userId);
        } catch (com.internship.orderservice.exception.NotFoundException e) {
            throw new com.internship.orderservice.exception.NotFoundException(
                    "User does not exist: " + userId
            );
        }
    }

    private UserResponse safeGetUser(Long userId) {
        try {
            return userClient.getByUserId(userId);
        } catch (com.internship.orderservice.exception.NotFoundException e) {
            return null;
        }
    }
}
