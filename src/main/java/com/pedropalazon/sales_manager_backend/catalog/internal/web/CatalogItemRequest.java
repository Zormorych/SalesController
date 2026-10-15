package com.pedropalazon.sales_manager_backend.catalog.internal.web;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CatalogItemRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
        String name,

        @NotNull(message = "El precio de venta es obligatorio")
        @PositiveOrZero(message = "El precio de venta no puede ser negativo")
        @Digits(integer = 8, fraction = 2, message = "El precio de venta admite como máximo 8 enteros y 2 decimales")
        BigDecimal salePrice,

        @NotNull(message = "El coste es obligatorio")
        @PositiveOrZero(message = "El coste no puede ser negativo")
        @Digits(integer = 8, fraction = 2, message = "El coste admite como máximo 8 enteros y 2 decimales")
        BigDecimal purchaseCost) {
}
