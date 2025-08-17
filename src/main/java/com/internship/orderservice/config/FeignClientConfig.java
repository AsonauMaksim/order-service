package com.internship.orderservice.config;

import com.internship.orderservice.exception.NotFoundException;
import com.internship.orderservice.exception.UnauthorizedException;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignClientConfig {

    @Bean
    public RequestInterceptor authForwardingInterceptor() {
        return template -> {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                String auth = sra.getRequest().getHeader("Authorization");
                if (auth != null && !auth.isBlank()) {
                    template.header("Authorization", auth);
                }
            }
        };
    }

    @Bean
    public ErrorDecoder feignErrorDecoder() {
        return (methodKey, response) -> switch (response.status()) {
            case 401 -> new UnauthorizedException("User Service: unauthorized");
            case 403 -> new AccessDeniedException("User Service: forbidden");
            case 404 -> new NotFoundException("User not found");
            default -> new RuntimeException("User Service error: " + response.status());
        };
    }
}