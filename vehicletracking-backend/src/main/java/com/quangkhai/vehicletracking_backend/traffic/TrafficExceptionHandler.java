package com.quangkhai.vehicletracking_backend.traffic;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = "com.quangkhai.vehicletracking_backend.traffic")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TrafficExceptionHandler {
    @ExceptionHandler(TrafficOperationException.class)
    public ResponseEntity<ProblemDetail> handle(TrafficOperationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problem.setTitle(ex.getErrorCode().name());
        problem.setProperty("code", ex.getErrorCode().name());
        return ResponseEntity.status(ex.getStatus()).body(problem);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleBoundsTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatus.BAD_REQUEST,
                "Traffic bounds phải là số hợp lệ.");
        problem.setTitle(TrafficErrorCode.TRAFFIC_BOUNDS_INVALID.name());
        problem.setProperty("code", TrafficErrorCode.TRAFFIC_BOUNDS_INVALID.name());
        return ResponseEntity.badRequest().body(problem);
    }
}
