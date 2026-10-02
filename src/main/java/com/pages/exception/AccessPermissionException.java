package com.pages.exception;

public class AccessPermissionException extends RuntimeException{
    private String message;
    private String cause;

    public AccessPermissionException(String message) {
        super(message);
    }

    public AccessPermissionException(String message, Throwable cause) {
        super(message, cause);
    }
}
