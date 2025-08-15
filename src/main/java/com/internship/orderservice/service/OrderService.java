package com.internship.orderservice.service;

import com.internship.orderservice.dto.request.OrderRequest;
import com.internship.orderservice.dto.response.OrderResponse;
import com.internship.orderservice.entity.OrderStatus;

import java.util.List;

public interface OrderService {

    OrderResponse createOrder(OrderRequest request);
    OrderResponse getOrderById(Long id);
    List<OrderResponse> getOrdersByIds(List<Long> ids);
    List<OrderResponse> getOrdersByStatuses(List<OrderStatus> statuses);
    OrderResponse updateOrder(Long id, OrderRequest request);
    void deleteOrder(Long id);
}
