package com.ureclive.urec_live_backend.exception;

import com.ureclive.urec_live_backend.dto.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex) {
        logger.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError(HttpStatus.UNAUTHORIZED.value(), "Invalid username or password"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationErrors(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));
        logger.warn("Validation error: {}", errorMessage);
        return ResponseEntity.badRequest()
                .body(new ApiError(HttpStatus.BAD_REQUEST.value(), errorMessage));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiError> handleRuntimeException(RuntimeException ex) {
        // Fallback for generic logic errors (e.g. "User already exists")
        if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
            logger.warn("Application error: {}", ex.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiError(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
        }
        logger.error("Unexpected runtime error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "An unexpected error occurred"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneralException(Exception ex) {
        logger.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "An internal server error occurred"));
    }
}
