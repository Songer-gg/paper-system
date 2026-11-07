package cn.paperhub.common.exception;

import cn.paperhub.common.api.ApiResponse;
import cn.paperhub.common.api.ErrorCode;
import javax.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> business(BusinessException ex) {
        return failure(ex.getErrorCode(), ex.getMessage());
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResponse<Void>> validation(Exception ex) {
        // Do not echo rejected values: a field may contain a password or token.
        return failure(ErrorCode.INVALID_PARAM, "请求参数为空、超长或格式不合法");
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class, HttpMediaTypeNotSupportedException.class,
            HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<ApiResponse<Void>> badRequest(Exception ex) {
        return failure(ErrorCode.INVALID_PARAM, "请求格式、字段或方法不合法");
    }
    @ExceptionHandler({org.springframework.web.multipart.MultipartException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    public ResponseEntity<ApiResponse<Void>> multipart(Exception ex) {
        return failure(ErrorCode.INVALID_PARAM, "文件缺失、大小超限或上传格式不合法");
    }
    @ExceptionHandler({DuplicateKeyException.class, org.springframework.dao.ConcurrencyFailureException.class})
    public ResponseEntity<ApiResponse<Void>> conflict(Exception ex) { return failure(ErrorCode.CONFLICT); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> integrity(Exception ex) { return failure(ErrorCode.INVALID_PARAM); }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> forbidden(AccessDeniedException ex) { return failure(ErrorCode.FORBIDDEN); }
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> notFound(NoHandlerFoundException ex) { return failure(ErrorCode.NOT_FOUND); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception ex) {
        LOG.error("Unhandled server failure", ex);
        return failure(ErrorCode.SERVER_ERROR);
    }
    private ResponseEntity<ApiResponse<Void>> failure(ErrorCode code) { return failure(code, code.getMessage()); }
    private ResponseEntity<ApiResponse<Void>> failure(ErrorCode code, String message) {
        return ResponseEntity.status(code.getStatus()).body(ApiResponse.failure(code, message));
    }
}
