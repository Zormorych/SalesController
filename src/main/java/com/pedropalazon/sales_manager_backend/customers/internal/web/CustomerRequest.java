package com.pedropalazon.sales_manager_backend.customers.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
        String name,

        // Vacío = sin teléfono (el frontend puede mandar "" en vez de null)
        @Pattern(regexp = "^$|^\\+?[0-9 .()-]{6,25}$",
                message = "El teléfono solo puede tener dígitos, espacios, guiones, puntos, paréntesis y un + inicial")
        String phone,

        @Size(max = 500, message = "Las notas no pueden superar 500 caracteres")
        String notes) {
}
