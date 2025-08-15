package com.internship.orderservice.exception;

public class InvalidTokenException extends RuntimeException{

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
