package com.internship.orderservice.dto.request;


import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderRequest {

//    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Status is required")
    @Pattern(
            regexp = "PENDING|PAID|PROCESSING|SHIPPED|DELIVERED|CANCELLED|FAILED",
            message = "Invalid status"
    )
    private String status;

    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<OrderItemRequest> items;
}