package com.acme.ecommerce.common.exception;

public class BusinessException extends ApplicationException {
    public BusinessException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
