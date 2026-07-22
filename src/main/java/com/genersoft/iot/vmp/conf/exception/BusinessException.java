package com.genersoft.iot.vmp.conf.exception;

import com.genersoft.iot.vmp.vmanager.bean.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 用户可理解、可修正的业务规则失败。
 */
public class BusinessException extends RuntimeException {

    private final int code;
    private final HttpStatus status;

    public BusinessException(String message) {
        this(ErrorCode.ERROR100.getCode(), message, HttpStatus.BAD_REQUEST);
    }

    public BusinessException(String message, HttpStatus status) {
        this(ErrorCode.ERROR100.getCode(), message, status);
    }

    public BusinessException(int code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status == null ? HttpStatus.BAD_REQUEST : status;
    }

    public int getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
