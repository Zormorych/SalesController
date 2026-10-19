package com.pedropalazon.sales_manager_backend.resale.internal.web;

import com.pedropalazon.sales_manager_backend.resale.internal.ResaleItemDraft;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

// purchaseDate es opcional (por defecto, hoy) y askingPrice también (puede no estar anunciada todavía)
public record ResaleItemRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 160, message = "El nombre no puede superar 160 caracteres")
        String name,

        @Size(max = 40, message = "La talla no puede superar 40 caracteres")
        String size,

        @Size(max = 500, message = "Las notas no pueden superar 500 caracteres")
        String notes,

        @NotNull(message = "El coste es obligatorio")
        @PositiveOrZero(message = "El coste no puede ser negativo")
        @Digits(integer = 8, fraction = 2, message = "El coste admite como máximo 8 enteros y 2 decimales")
        BigDecimal purchaseCost,

        LocalDate purchaseDate,

        @PositiveOrZero(message = "El precio anunciado no puede ser negativo")
        @Digits(integer = 8, fraction = 2, message = "El precio anunciado admite como máximo 8 enteros y 2 decimales")
        BigDecimal askingPrice) {

    ResaleItemDraft toDraft() {
        return new ResaleItemDraft(name, size, notes, purchaseCost, purchaseDate, askingPrice);
    }
}
