package com.internship.orderservice.client;

import com.internship.orderservice.dto.external.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", url = "${external.user-service.url}")
public interface UserClient {

    @GetMapping("/api/users/{id}")
    UserResponse getByUserId(@PathVariable("id") Long id);
}
