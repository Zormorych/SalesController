package com.pedropalazon.sales_manager_backend.reports.internal;

import com.pedropalazon.sales_manager_backend.resale.ResaleSales;
import java.math.BigDecimal;
import java.time.LocalDate;

// Resumen de un periodo. Venta, coste y envío son los de las camisetas no canceladas de los pedidos con fecha
// en el periodo; collected son los cobros con fecha de cobro en el periodo (pueden ser de pedidos anteriores).
// profit = sale - cost - shipping. resale: prendas de stock vendidas (por fecha de venta) en el periodo.
// totalProfit = profit + resale.profit
public record SalesSummary(LocalDate from, LocalDate to, long orders, long shirts, BigDecimal sale, BigDecimal cost,
        BigDecimal shipping, BigDecimal profit, BigDecimal collected, ResaleSales resale, BigDecimal totalProfit) {
}
