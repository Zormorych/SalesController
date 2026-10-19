package com.pedropalazon.sales_manager_backend.resale;

import java.math.BigDecimal;

// Ventas de stock de un periodo. profit = sale - cost
public record ResaleSales(long items, BigDecimal sale, BigDecimal cost, BigDecimal profit) {
}
