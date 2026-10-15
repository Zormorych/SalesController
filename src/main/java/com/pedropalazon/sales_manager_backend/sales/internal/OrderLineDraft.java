package com.pedropalazon.sales_manager_backend.sales.internal;

import com.pedropalazon.sales_manager_backend.catalog.ClothingSize;
import java.math.BigDecimal;
import java.util.List;

// Datos de una línea tal como llegan de fuera. Los precios a null significan "usar el del catálogo".
public record OrderLineDraft(Long productId, String kitName, ClothingSize size, BigDecimal unitSalePrice,
        BigDecimal unitPurchaseCost, List<Customization> customizations) {

    public OrderLineDraft {
        customizations = customizations == null ? List.of() : List.copyOf(customizations);
    }

    public record Customization(Long customizationId, String customText, BigDecimal salePrice,
            BigDecimal purchaseCost) {
    }
}
