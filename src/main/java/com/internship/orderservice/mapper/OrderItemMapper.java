package com.internship.orderservice.mapper;


import com.internship.orderservice.dto.response.OrderItemResponse;
import com.internship.orderservice.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrderItemMapper {

    @Mapping(source = "item.id", target = "itemId")
    @Mapping(source = "item.name", target = "itemName")
    OrderItemResponse toDto(OrderItem orderItem);
}
