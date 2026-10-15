package com.pedropalazon.sales_manager_backend.sales;

// API pública de ventas para otros módulos (por ejemplo, cobros necesita el total de un pedido)
public interface SalesOrderApi {

    // Lanza ResourceNotFoundException si no existe
    SalesOrderSnapshot getOrder(Long orderId);
}
