package com.pedropalazon.sales_manager_backend.resale.internal.web;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

// salePrice es el precio final (tras el regateo); saleDate es opcional: por defecto, hoy
public record SellResaleItemRequest(
        @NotNull(message = "El precio de venta es obligatorio")
        @PositiveOrZero(message = "El precio de venta no puede ser negativo")
        @Digits(integer = 8, fraction = 2, message = "El precio de venta admite como máximo 8 enteros y 2 decimales")
        BigDecimal salePrice,

        LocalDate saleDate) {
}
