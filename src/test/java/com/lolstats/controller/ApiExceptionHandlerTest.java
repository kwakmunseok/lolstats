package com.lolstats.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    // Real Method/MethodParameter (not mocked) - MethodArgumentNotValidException's constructor
    // needs one, and constructing it is simpler than mocking it.
    private void dummyTarget(String arg) {
    }

    private MethodArgumentNotValidException exceptionWithFieldErrors(FieldError... errors) throws NoSuchMethodException {
        Method method = getClass().getDeclaredMethod("dummyTarget", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        for (FieldError error : errors) {
            bindingResult.addError(error);
        }
        return new MethodArgumentNotValidException(parameter, bindingResult);
    }

    @Test
    void handleValidation_singleFieldError_includesFieldNameAndMessage() throws NoSuchMethodException {
        MethodArgumentNotValidException ex = exceptionWithFieldErrors(
                new FieldError("request", "email", "must be a well-formed email address"));

        ProblemDetail problem = handler.handleValidation(ex);

        assertEquals(400, problem.getStatus());
        assertEquals("email: must be a well-formed email address", problem.getDetail());
    }

    @Test
    void handleValidation_multipleFieldErrors_joinsAll() throws NoSuchMethodException {
        MethodArgumentNotValidException ex = exceptionWithFieldErrors(
                new FieldError("request", "email", "must be a well-formed email address"),
                new FieldError("request", "agreedToTerms", "약관에 동의해야 합니다"));

        ProblemDetail problem = handler.handleValidation(ex);

        assertTrue(problem.getDetail().contains("email: must be a well-formed email address"));
        assertTrue(problem.getDetail().contains("agreedToTerms: 약관에 동의해야 합니다"));
    }
}
