package com.pedropalazon.sales_manager_backend.purchasing.internal.web;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateFactoryBatchRequest(
        @NotNull(message = "El coste de envío es obligatorio")
        @PositiveOrZero(message = "El coste de envío no puede ser negativo")
        @Digits(integer = 8, fraction = 2, message = "El coste de envío admite como máximo 8 enteros y 2 decimales")
        BigDecimal shippingCost,

        @Size(max = 500, message = "Las notas no pueden superar 500 caracteres")
        String notes) {
}
