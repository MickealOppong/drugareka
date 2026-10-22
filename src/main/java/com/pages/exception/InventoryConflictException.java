package com.pages.exception;

// Simple embedded runtime exception class helper to trigger Spring rollbacks cleanly
public class InventoryConflictException extends RuntimeException {
    public InventoryConflictException(String message) {
        super(message);
    }
}
