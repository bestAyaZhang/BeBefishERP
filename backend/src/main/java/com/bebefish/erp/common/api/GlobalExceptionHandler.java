package com.bebefish.erp.common.api;

import com.bebefish.erp.auth.application.AuthException;
import com.bebefish.erp.file.domain.FileStorageException;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        return ResponseEntity
                .status(exception.status())
                .body(ApiResponse.failure(exception.code(), exception.getMessage(), null));
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthException(AuthException exception) {
        return ResponseEntity
                .status(statusOf(exception.code()))
                .body(ApiResponse.failure(exception.code(), exception.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.failure("VALIDATION_FAILED", "请求参数不完整或格式错误", null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException exception) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.failure("FORBIDDEN", "无权限", null));
    }

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileStorageException(FileStorageException exception) {
        log.error("文件存储失败: {}", exception.getMessage(), exception);
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.failure("FILE_STORAGE_UNAVAILABLE", exception.getMessage(), null));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException exception
    ) {
        log.warn("图片上传超过大小限制: {} bytes", exception.getMaxUploadSize());
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.failure("IMAGE_TOO_LARGE", "图片超过 10 MB", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        log.error("未处理的服务异常", exception);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure("INTERNAL_ERROR", "服务器内部错误", null));
    }

    private HttpStatus statusOf(String code) {
        return switch (code) {
            case "UNAUTHORIZED" -> HttpStatus.UNAUTHORIZED;
            case "USER_DISABLED" -> HttpStatus.FORBIDDEN;
            case "SMS_SEND_TOO_FREQUENT" -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
