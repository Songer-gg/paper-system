package cn.paperhub.common.api;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    UNAUTHORIZED(1001, HttpStatus.UNAUTHORIZED, "未登录或会话已失效"),
    FORBIDDEN(1002, HttpStatus.FORBIDDEN, "无权访问该资源"),
    NOT_FOUND(1003, HttpStatus.NOT_FOUND, "资源不存在或不可访问"),
    CONFLICT(1004, HttpStatus.CONFLICT, "资源或操作状态冲突"),
    INVALID_PARAM(1005, HttpStatus.UNPROCESSABLE_ENTITY, "请求参数不合法"),
    BIZ_REJECTED(1006, HttpStatus.BAD_REQUEST, "当前操作不符合业务规则"),
    SERVER_ERROR(1500, HttpStatus.INTERNAL_SERVER_ERROR, "服务暂时不可用");

    private final int code;
    private final HttpStatus status;
    private final String message;

    ErrorCode(int code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }
    public int getCode() { return code; }
    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
