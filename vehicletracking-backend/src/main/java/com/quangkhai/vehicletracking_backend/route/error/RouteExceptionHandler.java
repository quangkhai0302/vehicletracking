package com.quangkhai.vehicletracking_backend.route.error;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.quangkhai.vehicletracking_backend.route")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RouteExceptionHandler {

    @ExceptionHandler(RouteOperationException.class)
    public ResponseEntity<ProblemDetail> handleRouteOperationException(RouteOperationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problem.setTitle(ex.getErrorCode().name());
        problem.setProperty("code", ex.getErrorCode().name());
        return ResponseEntity.status(ex.getStatus()).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Validation failed");

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Validation Failed");
        problem.setProperty("code", RouteErrorCode.ROUTE_VALIDATION_FAILED.name());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }
}
