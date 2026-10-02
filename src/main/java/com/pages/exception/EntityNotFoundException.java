package com.pages.exception;

import org.springframework.http.HttpStatus;

public class EntityNotFoundException extends RuntimeException{

    private String message;
    private String cause;

    public EntityNotFoundException(String message) {
        super(message);
    }

    public EntityNotFoundException(String message,Throwable cause) {
        super(message,cause);
    }

}
