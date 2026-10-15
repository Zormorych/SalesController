package com.pedropalazon.sales_manager_backend.catalog;

import java.math.BigDecimal;

// Foto de un producto o personalización en un momento dado: nombre y precios vigentes
public record CatalogItemSnapshot(Long id, String name, BigDecimal salePrice, BigDecimal purchaseCost) {
}
