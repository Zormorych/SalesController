package com.pedropalazon.sales_manager_backend.resale;

import java.time.LocalDate;

// API pública del módulo de stock y reventa: la usan los informes
public interface ResaleApi {

    // Prendas vendidas con fecha de venta entre from y to (ambas incluidas)
    ResaleSales soldBetween(LocalDate from, LocalDate to);

    // Prendas disponibles y el dinero parado en ellas (suma de sus costes)
    StockValue stock();
}
