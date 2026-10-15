package com.pedropalazon.sales_manager_backend.sales;

// API pública de ventas para otros módulos (por ejemplo, cobros necesita el total de un pedido)
public interface SalesOrderApi {

    // Lanza ResourceNotFoundException si no existe
    SalesOrderSnapshot getOrder(Long orderId);

    // Igual que getOrder, pero bloquea el pedido hasta que termine la transacción de quien llama.
    // Sirve para que dos operaciones simultáneas sobre el mismo pedido (por ejemplo, dos cobros)
    // se ejecuten una detrás de otra. Exige una transacción en curso.
    SalesOrderSnapshot lockOrder(Long orderId);
}
