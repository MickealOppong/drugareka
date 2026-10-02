package com.pages.exception;

import org.springframework.http.HttpStatus;

public class InvalidOperationException  extends RuntimeException{

    public InvalidOperationException(String message) {
        super(message);
    }

    public InvalidOperationException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidOperationException(String errorMessage, HttpStatus httpStatus) {
    }
}
