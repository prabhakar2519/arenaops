package com.arena.login.exception;

import lombok.Getter;

@Getter
public class ArenaOpsException extends RuntimeException {
    private final ErrorCode errorCode;

    public ArenaOpsException(ErrorCode errorCode) {
        super(errorCode.getDescription());
        this.errorCode = errorCode;
    }
}
