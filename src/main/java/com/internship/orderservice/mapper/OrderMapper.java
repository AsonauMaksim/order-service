package com.internship.orderservice.mapper;

import com.internship.orderservice.dto.external.UserResponse;
import com.internship.orderservice.dto.request.OrderRequest;
import com.internship.orderservice.dto.response.OrderResponse;
import com.internship.orderservice.entity.Order;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        uses = { OrderItemMapper.class, UserInfoMapper.class },
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface OrderMapper {

    @Mapping(target = "id",             ignore = true)
    @Mapping(target = "status",         ignore = true)
    @Mapping(target = "creationDate",   ignore = true)
    @Mapping(target = "orderItems",     ignore = true)
    Order toEntity(OrderRequest request);

    @Mapping(source = "orderItems", target = "items")
    OrderResponse toDto(Order order);

    @Mapping(source = "order.id",           target = "id")
    @Mapping(source = "order.userId",       target = "userId")
    @Mapping(source = "order.status",       target = "status")
    @Mapping(source = "order.creationDate", target = "creationDate")
    @Mapping(source = "order.orderItems",   target = "items")
    @Mapping(source = "user",               target = "user")
    OrderResponse toDto(Order order, UserResponse user);
}
