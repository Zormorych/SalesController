package com.pedropalazon.sales_manager_backend.sales;

// Estado de una camiseta de un pedido. Es API pública: otros módulos (lotes al fabricante) lo leen.
// PENDING → ORDERED → RECEIVED → DELIVERED. Si no llega del fabricante vuelve de ORDERED a PENDING.
// Una recibida que el cliente ya no quiere pasa a stock: STOCKED_PAID si la paga igualmente (sigue siendo
// una venta) o STOCKED_REFUNDED si se le devuelve el dinero (cuenta como cancelada).
public enum LineStatus {
    PENDING,
    ORDERED,
    RECEIVED,
    DELIVERED,
    CANCELLED,
    STOCKED_PAID,
    STOCKED_REFUNDED
}
