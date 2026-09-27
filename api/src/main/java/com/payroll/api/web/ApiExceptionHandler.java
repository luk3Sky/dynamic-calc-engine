package com.payroll.api.web;

import com.payroll.api.web.dto.ApiError;
import com.payroll.rulesengine.orchestration.EligibilityEvaluationException;
import com.payroll.rulesengine.orchestration.FormulaEvaluationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps exceptions to structured {@link ApiError} responses. Stack traces are
 * logged server-side and never leaked to the client.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Request validation failed");
        return ApiError.of(400, "Bad Request", message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleUnreadable(Exception ex) {
        log.info("Malformed request: {}", ex.getMessage());
        return ApiError.of(400, "Bad Request", "Malformed or unreadable request body");
    }

    @ExceptionHandler({EligibilityEvaluationException.class, FormulaEvaluationException.class})
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ApiError handleRuleEvaluation(RuntimeException ex) {
        log.warn("Rule evaluation failed: {}", ex.getMessage());
        return ApiError.of(422, "Rule Evaluation Failed", ex.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleNotFound(NoResourceFoundException ex) {
        return ApiError.of(404, "Not Found", "No handler found for " + ex.getResourcePath());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return ApiError.of(500, "Internal Server Error", "Unexpected server error");
    }
}