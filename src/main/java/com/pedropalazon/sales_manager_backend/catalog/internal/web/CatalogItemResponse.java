package com.pedropalazon.sales_manager_backend.catalog.internal.web;

import com.pedropalazon.sales_manager_backend.catalog.internal.CatalogItem;
import java.math.BigDecimal;

public record CatalogItemResponse(Long id, String name, BigDecimal salePrice, BigDecimal purchaseCost,
        String description, boolean active) {

    static CatalogItemResponse from(CatalogItem item) {
        return new CatalogItemResponse(item.getId(), item.getName(), item.getSalePrice(), item.getPurchaseCost(),
                item.getDescription(), item.isActive());
    }
}
