package com.internship.orderservice.dto.request;


import com.internship.orderservice.validation.Create;
import com.internship.orderservice.validation.Update;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Null;
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

    private Long userId;

    @Null(message = "Status is server-managed in create", groups = Create.class)
    @NotNull(message = "Status is required", groups = Update.class)
    @Pattern(
            regexp = "PENDING|PAID|PAYMENT_FAILED|PROCESSING|SHIPPED|DELIVERED|CANCELLED|FAILED",
            message = "Invalid status",
            groups = Update.class
    )
    private String status;

    @NotEmpty(message = "Order must contain at least one item", groups = {Create.class, Update.class})
    @Valid
    private List<OrderItemRequest> items;
}