package com.acme.ecommerce.common.exception;

public class ForbiddenOperationException extends ApplicationException {
    public ForbiddenOperationException(String message) {
        super(ErrorCode.ACCESS_DENIED, message);
    }
}
