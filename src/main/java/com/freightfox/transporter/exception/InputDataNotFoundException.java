package com.freightfox.transporter.exception;

/**
 * Thrown when the assignment API is called but no input data has been saved yet.
 * Results in HTTP 404 Not Found.
 */
public class InputDataNotFoundException extends RuntimeException {

    public InputDataNotFoundException(String message) {
        super(message);
    }
}

