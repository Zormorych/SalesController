package com.pedropalazon.sales_manager_backend.sales.internal.web;

import com.pedropalazon.sales_manager_backend.sales.internal.OrderLineDraft;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

// Precios opcionales: si no vienen se copian del catálogo; si vienen (también 0) se usan tal cual
public record LineCustomizationRequest(
        @NotNull(message = "La personalización es obligatoria")
        Long customizationId,

        @Size(max = 120, message = "El texto de la personalización no puede superar 120 caracteres")
        String customText,

        @PositiveOrZero(message = "El precio de venta no puede ser negativo")
        @Digits(integer = 8, fraction = 2, message = "El precio de venta admite como máximo 8 enteros y 2 decimales")
        BigDecimal salePrice,

        @PositiveOrZero(message = "El coste no puede ser negativo")
        @Digits(integer = 8, fraction = 2, message = "El coste admite como máximo 8 enteros y 2 decimales")
        BigDecimal purchaseCost) {

    OrderLineDraft.Customization toDraft() {
        return new OrderLineDraft.Customization(customizationId, customText, salePrice, purchaseCost);
    }
}
