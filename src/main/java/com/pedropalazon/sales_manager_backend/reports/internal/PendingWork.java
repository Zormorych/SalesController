package com.pedropalazon.sales_manager_backend.reports.internal;

import java.math.BigDecimal;

// Lo que queda por hacer: camisetas por pedir al fabricante, por recibir y por entregar; dinero por cobrar
// (pedidos con algo pendiente) y por devolver (lo cobrado de más, por ejemplo en pedidos cancelados);
// y las prendas disponibles en stock con el dinero parado en ellas (suma de sus costes)
public record PendingWork(long shirtsToOrder, long shirtsToReceive, long shirtsToDeliver, long ordersToCollect,
        BigDecimal amountToCollect, BigDecimal amountToRefund, long itemsInStock, BigDecimal stockValue) {
}
