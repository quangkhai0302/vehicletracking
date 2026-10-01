package com.quangkhai.vehicletracking_backend.assignment.controller;

import com.quangkhai.vehicletracking_backend.driverportal.controller.DriverAssignmentController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = DriverAssignmentController.class)
public class AssignmentValidationAdvice {
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ProblemDetail> invalidRequest(Exception ignored) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Dữ liệu yêu cầu nhận chuyến không hợp lệ.");
        body.setProperty("code", "ASSIGNMENT_VALIDATION_FAILED");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }
}
