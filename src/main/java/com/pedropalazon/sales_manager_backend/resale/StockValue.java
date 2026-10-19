package com.pedropalazon.sales_manager_backend.resale;

import java.math.BigDecimal;

// Prendas disponibles en stock y la suma de sus costes
public record StockValue(long items, BigDecimal cost) {
}
