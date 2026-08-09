package com.lolstats.controller;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// spring.mvc.problemdetails.enabled=true already normalizes ResponseStatusException (used by
// MatchController's 404s) into this same ProblemDetail shape - the one gap is
// @Validated path/query param failures, which otherwise surface as a raw 500. Future Phases
// add handlers here rather than each growing their own ad hoc error response.
// @Order forces this ahead of Boot's own internal MethodArgumentNotValidException handling
// (mvc.problemdetails.enabled=true's generic "Invalid request content." message) - confirmed
// live that without it, Boot's handler wins even though this bean is registered and its other
// handler (ConstraintViolationException) already fires correctly.
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Validation failed");
        return problem;
    }

    // Same gap as above, for @Valid @RequestBody failures (Phase 5 Task 6) - Boot's built-in
    // problemdetails handling already returns 400 for this without any handler here, but with a
    // generic "Invalid request content." detail. Forms (login/signup, Task 5) need to know
    // *which* field failed and why.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Validation failed");
        return problem;
    }
}
