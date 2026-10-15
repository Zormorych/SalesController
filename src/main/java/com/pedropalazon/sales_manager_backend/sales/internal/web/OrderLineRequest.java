package com.pedropalazon.sales_manager_backend.sales.internal.web;

import com.pedropalazon.sales_manager_backend.catalog.ClothingSize;
import com.pedropalazon.sales_manager_backend.sales.internal.OrderLineDraft;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

// Precios opcionales: si no vienen se copian del catálogo; si vienen (también 0) se usan tal cual
public record OrderLineRequest(
        @NotNull(message = "El producto es obligatorio")
        Long productId,

        @NotBlank(message = "La equipación es obligatoria")
        @Size(max = 160, message = "La equipación no puede superar 160 caracteres")
        String kitName,

        @NotNull(message = "La talla es obligatoria")
        ClothingSize size,

        @PositiveOrZero(message = "El precio de venta no puede ser negativo")
        @Digits(integer = 8, fraction = 2, message = "El precio de venta admite como máximo 8 enteros y 2 decimales")
        BigDecimal unitSalePrice,

        @PositiveOrZero(message = "El coste no puede ser negativo")
        @Digits(integer = 8, fraction = 2, message = "El coste admite como máximo 8 enteros y 2 decimales")
        BigDecimal unitPurchaseCost,

        @Valid
        List<LineCustomizationRequest> customizations) {

    OrderLineDraft toDraft() {
        List<OrderLineDraft.Customization> drafts = customizations == null
                ? List.of()
                : customizations.stream().map(LineCustomizationRequest::toDraft).toList();
        return new OrderLineDraft(productId, kitName, size, unitSalePrice, unitPurchaseCost, drafts);
    }
}
