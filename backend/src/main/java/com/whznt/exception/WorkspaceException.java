package com.whznt.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class WorkspaceException extends RuntimeException {

    private final HttpStatus status;

    public WorkspaceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static WorkspaceException badRequest(String message) {
        return new WorkspaceException(HttpStatus.BAD_REQUEST, message);
    }

    public static WorkspaceException notFound(String message) {
        return new WorkspaceException(HttpStatus.NOT_FOUND, message);
    }

    public static WorkspaceException conflict(String message) {
        return new WorkspaceException(HttpStatus.CONFLICT, message);
    }

    public static WorkspaceException unavailable(String message) {
        return new WorkspaceException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
