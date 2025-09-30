package com.internship.orderservice.dto.request;

import com.internship.orderservice.validation.Create;
import com.internship.orderservice.validation.Update;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemRequest {

    @NotNull(message = "Item ID is required", groups = {Create.class, Update.class})
    private Long itemId;

    @NotNull(message = "Quantity is required", groups = {Create.class, Update.class})
    @Min(value = 1, message = "Quantity must be at least 1", groups = {Create.class, Update.class})
    private Integer quantity;
}
