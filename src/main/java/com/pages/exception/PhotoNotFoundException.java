package com.pages.exception;

public class PhotoNotFoundException extends RuntimeException{

    public PhotoNotFoundException(String message){
        super(message);
    }

    public PhotoNotFoundException(String message,Throwable cause){
        super(message,cause);
    }
}
