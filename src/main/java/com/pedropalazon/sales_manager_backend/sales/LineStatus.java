package com.pedropalazon.sales_manager_backend.sales;

// Estado de una camiseta de un pedido. Es API pública: otros módulos (lotes al fabricante) lo leen.
// RECEIVED y DELIVERED llegarán con la recepción y la entrega.
public enum LineStatus {
    PENDING,
    ORDERED,
    CANCELLED
}
