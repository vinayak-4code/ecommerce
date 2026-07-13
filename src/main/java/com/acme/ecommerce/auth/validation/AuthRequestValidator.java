package com.acme.ecommerce.auth.validation;

import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class AuthRequestValidator {
    private static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).+$";

    public void validatePassword(String password) {
        if (password == null || password.length() < 8 || !password.matches(PASSWORD_PATTERN)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "Password must be at least 8 characters and include at least one letter and one number"
            );
        }
    }

    public String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
