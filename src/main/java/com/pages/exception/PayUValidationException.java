package com.pages.exception;

import lombok.Data;

@Data
public class PayUValidationException extends RuntimeException {
    private final int statusCode;
    private final String payuErrorCode;

    public PayUValidationException(int statusCode, String payuErrorCode, String message) {
        super(message);
        this.statusCode = statusCode;
        this.payuErrorCode = payuErrorCode;
    }

    public int getStatusCode() { return statusCode; }
    public String getPayuErrorCode() { return payuErrorCode; }
}
