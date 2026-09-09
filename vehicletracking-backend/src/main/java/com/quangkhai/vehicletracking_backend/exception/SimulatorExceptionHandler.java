package com.quangkhai.vehiceltracking_backend.exception;

import com.quangkhai.vehiceltracking_backend.controller.SimulatorController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice(assignableTypes = SimulatorController.class)
public class SimulatorExceptionHandler {

    private static final URI ABOUT_BLANK = URI.create("about:blank");
    private static final MediaType PROBLEM_JSON = MediaType.parseMediaType("application/problem+json");

    @ExceptionHandler(SimulatorNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleSimulatorNotFound(SimulatorNotFoundException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(ABOUT_BLANK);
        problem.setTitle("Not Found");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(PROBLEM_JSON).body(problem);
    }

    @ExceptionHandler(SimulatorConflictException.class)
    public ResponseEntity<ProblemDetail> handleSimulatorConflict(SimulatorConflictException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(ABOUT_BLANK);
        problem.setTitle("Conflict");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.CONFLICT).contentType(PROBLEM_JSON).body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(ABOUT_BLANK);
        problem.setTitle("Bad Request");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(PROBLEM_JSON).body(problem);
    }
}
