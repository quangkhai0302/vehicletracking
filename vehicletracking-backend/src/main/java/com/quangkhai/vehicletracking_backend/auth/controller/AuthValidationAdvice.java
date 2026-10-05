package com.quangkhai.vehicletracking_backend.auth.controller;

import com.quangkhai.vehicletracking_backend.auth.dto.DriverPasswordChangeRequest;
import com.quangkhai.vehicletracking_backend.auth.dto.DriverPasswordResetRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {AuthController.class, UserAccountController.class})
public class AuthValidationAdvice {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> invalid(MethodArgumentNotValidException exception) {
        Class<?> requestType = exception.getParameter().getParameterType();
        String detail = "Invalid request content.";
        if (requestType == DriverPasswordChangeRequest.class || requestType == DriverPasswordResetRequest.class) {
            detail = exception.getBindingResult().getAllErrors().stream()
                    .map(error -> error.getDefaultMessage())
                    .filter(message -> message != null && !message.isBlank())
                    .findFirst().orElse("Thông tin mật khẩu không hợp lệ.");
        }
        // Return only a constraint message. Field errors also contain rejected
        // credentials and must never be copied into the response.
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }
}
