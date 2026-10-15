package com.pedropalazon.sales_manager_backend.shared.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pedropalazon.sales_manager_backend.shared.BusinessRuleException;
import com.pedropalazon.sales_manager_backend.shared.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.FailingController.class)
@Import(GlobalExceptionHandlerTest.FailingController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void givenMissingResource_whenRequested_thenReturnsNotFoundProblem() throws Exception {
        mockMvc.perform(get("/test-errors/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.detail").value("Pedido 7 no encontrado"));
    }

    @Test
    void givenBrokenBusinessRule_whenRequested_thenReturnsConflictProblem() throws Exception {
        mockMvc.perform(get("/test-errors/business-rule"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Operación no permitida"))
                .andExpect(jsonPath("$.detail").value("El pedido ya está entregado"));
    }

    @Test
    void givenUnexpectedError_whenRequested_thenReturnsGenericProblemWithoutInternalMessage() throws Exception {
        mockMvc.perform(get("/test-errors/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Error inesperado"))
                .andExpect(content().string(not(containsString("secreto interno"))));
    }

    @Test
    void givenInvalidFields_whenPosted_thenReturnsBadRequestWithOneErrorPerField() throws Exception {
        mockMvc.perform(post("/test-errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"price\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Datos no válidos"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].message").value("El nombre es obligatorio"))
                .andExpect(jsonPath("$.errors[?(@.field == 'price')].message").value("El precio no puede ser negativo"));
    }

    @Test
    void givenMalformedJson_whenPosted_thenReturnsBadRequestProblem() throws Exception {
        mockMvc.perform(post("/test-errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Petición no válida"))
                .andExpect(jsonPath("$.detail").value("El cuerpo de la petición no tiene un formato válido."));
    }

    @Test
    void givenWrongValueType_whenPosted_thenReturnsBadRequestProblemInSpanish() throws Exception {
        mockMvc.perform(post("/test-errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Camiseta\",\"price\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Petición no válida"));
    }

    @Test
    void givenUnknownUrl_whenRequested_thenReturnsNotFoundProblemInSpanish() throws Exception {
        mockMvc.perform(get("/test-errors/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.detail").value("La ruta solicitada no existe."));
    }

    @Test
    void givenUnsupportedMethod_whenRequested_thenReturnsMethodNotAllowedProblemInSpanish() throws Exception {
        mockMvc.perform(post("/test-errors/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.title").value("Método no permitido"));
    }

    @Test
    void givenMissingRequestParameter_whenRequested_thenReturnsBadRequestProblemInSpanish() throws Exception {
        mockMvc.perform(get("/test-errors/param-validation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Petición no válida"))
                .andExpect(jsonPath("$.detail").value("Falta el parámetro obligatorio page."));
    }

    @Test
    void givenInvalidRequestParameter_whenRequested_thenReturnsBadRequestWithFieldError() throws Exception {
        mockMvc.perform(get("/test-errors/param-validation").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Datos no válidos"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("page"))
                .andExpect(jsonPath("$.errors[0].message").value("La página debe ser al menos 1"));
    }

    @RestController
    @RequestMapping("/test-errors")
    static class FailingController {

        @GetMapping("/not-found")
        void notFound() {
            throw new ResourceNotFoundException("Pedido 7 no encontrado");
        }

        @GetMapping("/business-rule")
        void businessRule() {
            throw new BusinessRuleException("El pedido ya está entregado");
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("secreto interno");
        }

        @PostMapping("/validation")
        void validation(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/param-validation")
        void paramValidation(@RequestParam @Min(value = 1, message = "La página debe ser al menos 1") int page) {
        }
    }

    public record TestRequest(
            @NotBlank(message = "El nombre es obligatorio") String name,
            @PositiveOrZero(message = "El precio no puede ser negativo") BigDecimal price) {
    }
}
