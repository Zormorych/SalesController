package com.pedropalazon.sales_manager_backend.resale.internal;

import java.math.BigDecimal;
import java.time.LocalDate;

// Una prenda de stock. profit = salePrice - purchaseCost, solo si está vendida
public record ResaleItemView(Long id, String name, String size, String notes, ResaleItemOrigin origin,
        Long salesOrderLineId, BigDecimal purchaseCost, LocalDate purchaseDate, BigDecimal askingPrice,
        ResaleItemStatus status, BigDecimal salePrice, LocalDate saleDate, BigDecimal profit) {

    static ResaleItemView of(ResaleItem item) {
        return new ResaleItemView(item.getId(), item.getName(), item.getSize(), item.getNotes(), item.getOrigin(),
                item.getSalesOrderLineId(), item.getPurchaseCost(), item.getPurchaseDate(), item.getAskingPrice(),
                item.getStatus(), item.getSalePrice(), item.getSaleDate(), item.profit());
    }
}
