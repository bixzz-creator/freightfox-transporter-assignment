package com.freightfox.transporter.exception;

/**
 * Thrown when the number of transporters exceeds the maximum supported by the DP algorithm.
 * Results in HTTP 400 Bad Request.
 */
public class TransporterLimitExceededException extends RuntimeException {

    public TransporterLimitExceededException(String message) {
        super(message);
    }
}

