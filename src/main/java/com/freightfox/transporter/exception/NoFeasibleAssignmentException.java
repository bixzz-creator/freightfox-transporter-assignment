package com.freightfox.transporter.exception;

/**
 * Thrown when no feasible assignment exists for the given data and constraints.
 * Results in HTTP 422 Unprocessable Entity.
 */
public class NoFeasibleAssignmentException extends RuntimeException {

    public NoFeasibleAssignmentException(String message) {
        super(message);
    }
}

