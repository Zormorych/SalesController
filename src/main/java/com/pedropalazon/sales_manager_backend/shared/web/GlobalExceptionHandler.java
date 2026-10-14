package com.pedropalazon.sales_manager_backend.shared.web;

import com.pedropalazon.sales_manager_backend.shared.BusinessRuleException;
import com.pedropalazon.sales_manager_backend.shared.ResourceNotFoundException;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

// Extiende ResponseEntityExceptionHandler para que los errores propios de Spring MVC
// (JSON mal formado, URL inexistente, método no soportado...) también salgan como ProblemDetail.
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleResourceNotFound(ResourceNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Recurso no encontrado", exception.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail handleBusinessRule(BusinessRuleException exception) {
        return problem(HttpStatus.CONFLICT, "Operación no permitida", exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unexpected error while processing request", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error inesperado",
                "Ha ocurrido un error inesperado. Inténtalo de nuevo más tarde.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Datos no válidos", "Hay campos con valores no válidos.");
        List<FieldValidationError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldValidationError(error.getField(),
                        Objects.requireNonNullElse(error.getDefaultMessage(), "Valor no válido")))
                .toList();
        body.setProperty("errors", errors);
        return handleExceptionInternal(exception, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }

    public record FieldValidationError(String field, String message) {
    }
}
