package com.menval.couriererp.tenant.advice;

import com.menval.couriererp.tenant.exceptions.TenantAccessDeniedException;
import com.menval.couriererp.tenant.exceptions.TenantExpiredException;
import com.menval.couriererp.tenant.exceptions.TenantSuspendedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class TenantExceptionHandler {

    @ExceptionHandler({TenantAccessDeniedException.class, TenantSuspendedException.class, TenantExpiredException.class})
    public ResponseEntity<Map<String, String>> handleTenantAccessException(RuntimeException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", ex.getMessage()));
    }
}
