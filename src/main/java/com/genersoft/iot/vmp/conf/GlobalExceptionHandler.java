package com.genersoft.iot.vmp.conf;

import com.genersoft.iot.vmp.auth.security.SecurityError;
import com.genersoft.iot.vmp.auth.security.RequestIdResolver;
import com.genersoft.iot.vmp.authorization.video.resolver.AmbiguousVideoResourceException;
import com.genersoft.iot.vmp.authorization.video.resolver.VideoResourceNotFoundException;
import com.genersoft.iot.vmp.conf.exception.ControllerException;
import com.genersoft.iot.vmp.media.security.StreamShareAccessException;
import com.genersoft.iot.vmp.media.security.DownloadTicketException;
import com.genersoft.iot.vmp.vmanager.bean.ErrorCode;
import com.genersoft.iot.vmp.vmanager.bean.WVPResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DownloadTicketException.class)
    public ResponseEntity<WVPResult<String>> handleDownloadTicket(
            DownloadTicketException exception) {
        HttpStatus status = switch (exception.reason()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case EXPIRED, AUTHORIZATION_CHANGED -> HttpStatus.GONE;
            case TARGET_UNAVAILABLE -> HttpStatus.NOT_FOUND;
        };
        return ResponseEntity.status(status).body(WVPResult.fail(
                status.value(), exception.reason().name()));
    }

    @ExceptionHandler(StreamShareAccessException.class)
    public ResponseEntity<WVPResult<String>> handleStreamShareAccess(
            StreamShareAccessException exception) {
        HttpStatus status = switch (exception.reason()) {
            case PASSCODE_REQUIRED, PASSCODE_INVALID -> HttpStatus.FORBIDDEN;
            case EXPIRED, REVOKED, EXHAUSTED, AUTHORIZATION_CHANGED -> HttpStatus.GONE;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
        };
        return ResponseEntity.status(status).body(WVPResult.fail(
                status.value(), exception.reason().name()));
    }

    @ExceptionHandler(AuthenticationServiceException.class)
    public ResponseEntity<WVPResult<String>> handleAuthenticationServiceFailure(
            AuthenticationServiceException exception) {
        log.error("认证依赖服务不可用", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(WVPResult.fail(503, "认证服务暂时不可用"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<SecurityError> handleAuthenticationFailure(
            AuthenticationException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new SecurityError(
                        ErrorCode.ERROR401.getCode(),
                        "用户名或密码错误",
                        RequestIdResolver.resolve(request)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<SecurityError> handleAccessDenied(
            AccessDeniedException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new SecurityError(
                        ErrorCode.ERROR403.getCode(),
                        ErrorCode.ERROR403.getMsg(),
                        RequestIdResolver.resolve(request)));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<WVPResult<String>> handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(WVPResult.fail(413, "文件过大"));
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<WVPResult<String>> handleNotFound(Exception exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(WVPResult.fail(ErrorCode.ERROR404));
    }

    @ExceptionHandler({
            VideoResourceNotFoundException.class,
            AmbiguousVideoResourceException.class
    })
    public ResponseEntity<WVPResult<String>> handleVideoResourceNotFound(
            RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(WVPResult.fail(ErrorCode.ERROR404));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<WVPResult<String>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(WVPResult.fail(ErrorCode.ERROR400.getCode(), "请求方法不支持"));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<WVPResult<String>> handleBadRequest(Exception exception) {
        return ResponseEntity.badRequest().body(WVPResult.fail(ErrorCode.ERROR400));
    }

    @ExceptionHandler(ControllerException.class)
    public ResponseEntity<WVPResult<String>> handleControllerException(ControllerException exception) {
        HttpStatus status = switch (exception.getCode()) {
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.OK;
        };
        return ResponseEntity.status(status)
                .body(WVPResult.fail(exception.getCode(), exception.getMsg()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<WVPResult<String>> handleUnexpectedException(Exception exception) {
        log.error("[全局异常]", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(WVPResult.fail(ErrorCode.ERROR500));
    }
}
