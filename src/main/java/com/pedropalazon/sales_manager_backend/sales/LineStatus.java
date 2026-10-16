package com.pedropalazon.sales_manager_backend.sales;

// Estado de una camiseta de un pedido. Es API pública: otros módulos (lotes al fabricante) lo leen.
// PENDING → ORDERED → RECEIVED → DELIVERED. Si no llega del fabricante vuelve de ORDERED a PENDING.
public enum LineStatus {
    PENDING,
    ORDERED,
    RECEIVED,
    DELIVERED,
    CANCELLED
}
