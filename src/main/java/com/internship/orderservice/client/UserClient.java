package com.internship.orderservice.client;

import com.internship.orderservice.config.FeignClientConfig;
import com.internship.orderservice.dto.external.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "user-service",
        url = "${external.user-service.url}",
        configuration = FeignClientConfig.class
)
public interface UserClient {

    @GetMapping("/api/users/{id}")
    UserResponse getByUserId(@PathVariable("id") Long id);

    @GetMapping("/api/users/by-credentials-id/{credentialsId}")
    UserResponse getByCredentialsId(@PathVariable("credentialsId") Long credentialsId);
}
