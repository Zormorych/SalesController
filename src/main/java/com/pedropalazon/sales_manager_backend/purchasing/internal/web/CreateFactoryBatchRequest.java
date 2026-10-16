package com.pedropalazon.sales_manager_backend.purchasing.internal.web;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

// El coste de envío por defecto (normalmente 4,00 €) lo propone el frontend
public record CreateFactoryBatchRequest(
        @NotEmpty(message = "El lote necesita al menos una camiseta")
        List<@NotNull(message = "La línea no puede estar vacía") Long> lineIds,

        @NotNull(message = "El coste de envío es obligatorio")
        @PositiveOrZero(message = "El coste de envío no puede ser negativo")
        @Digits(integer = 8, fraction = 2, message = "El coste de envío admite como máximo 8 enteros y 2 decimales")
        BigDecimal shippingCost,

        @Size(max = 500, message = "Las notas no pueden superar 500 caracteres")
        String notes) {
}
