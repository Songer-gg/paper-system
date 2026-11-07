package cn.paperhub.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public final class ApiResponse<T> {
    private final int code;
    private final String message;
    private final T data;

    private ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }
    public static <T> ApiResponse<T> success(T data) { return new ApiResponse<>(0, "ok", data); }
    public static ApiResponse<Void> failure(ErrorCode code) { return failure(code, code.getMessage()); }
    public static ApiResponse<Void> failure(ErrorCode code, String message) {
        return new ApiResponse<>(code.getCode(), message, null);
    }
    public int getCode() { return code; }
    public String getMessage() { return message; }
    public T getData() { return data; }
}
