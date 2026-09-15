package com.pages.exception;

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
