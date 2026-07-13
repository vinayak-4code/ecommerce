package com.acme.ecommerce.common.exception;

public class DuplicateResourceException extends ApplicationException {
    public DuplicateResourceException(String message) {
        super(ErrorCode.DUPLICATE_RESOURCE, message);
    }
}
