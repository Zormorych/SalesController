package com.pedropalazon.sales_manager_backend.sales;

import java.math.BigDecimal;

// Datos de un pedido que necesitan otros módulos. totalSale ya excluye lo cancelado (0 si el pedido lo está).
public record SalesOrderSnapshot(Long id, String orderNumber, Long customerId, boolean cancelled,
        BigDecimal totalSale) {
}
