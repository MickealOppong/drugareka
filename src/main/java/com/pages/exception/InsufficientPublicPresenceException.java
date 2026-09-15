package com.pages.exception;

public class InsufficientPublicPresenceException extends RuntimeException{
    public InsufficientPublicPresenceException(String message) {
        super(message);
    }

    public InsufficientPublicPresenceException(String message,Throwable cause) {
        super(message,cause);
    }
}
