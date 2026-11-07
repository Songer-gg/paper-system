package cn.paperhub.common.exception;

import cn.paperhub.common.api.ErrorCode;

public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    public BusinessException(ErrorCode errorCode) { this(errorCode, errorCode.getMessage()); }
    public ErrorCode getErrorCode() { return errorCode; }
}
