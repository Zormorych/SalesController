package com.pedropalazon.sales_manager_backend.sales.internal;

// Progreso de un pedido calculado a partir de sus camisetas no canceladas (no se guarda en BD)
public enum SalesOrderProgress {
    PENDING,
    IN_PROGRESS,
    READY_TO_DELIVER,
    COMPLETED,
    CANCELLED
}
