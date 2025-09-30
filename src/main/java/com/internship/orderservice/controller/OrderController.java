package com.internship.orderservice.controller;

import com.internship.orderservice.dto.request.OrderRequest;
import com.internship.orderservice.dto.response.OrderResponse;
import com.internship.orderservice.entity.OrderStatus;
import com.internship.orderservice.service.OrderService;
import com.internship.orderservice.validation.Create;
import com.internship.orderservice.validation.Update;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Validated
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader("X-User-Id") Long credentialsId,
            @RequestBody @Validated(Create.class) OrderRequest request
    ) {

        request.setUserId(credentialsId);
        OrderResponse resp = orderService.createOrder(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(resp.getId())
                .toUri();

        return ResponseEntity.created(location).body(resp);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        OrderResponse response = orderService.getOrderById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/by-ids")
    public ResponseEntity<List<OrderResponse>> getOrdersByIds(@RequestParam @NotEmpty List<Long> ids) {
        List<OrderResponse> responses = orderService.getOrdersByIds(ids);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/by-statuses")
    public ResponseEntity<List<OrderResponse>> getOrdersByStatuses(@RequestParam @NotEmpty List<OrderStatus> statuses) {
        List<OrderResponse> responses = orderService.getOrdersByStatuses(statuses);
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrderResponse> updateOrder(
            @RequestHeader("X-User-Id") Long credentialsId,
            @PathVariable Long id,
//            @RequestBody @Valid OrderRequest request
            @RequestBody @Validated(Update.class) OrderRequest request
    ) {

        OrderResponse response = orderService.updateOrder(id, request, credentialsId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOrder(
            @RequestHeader("X-User-Id") Long credentialsId,
            @PathVariable Long id
    ) {
        orderService.deleteOrder(id, credentialsId);
    }
}
