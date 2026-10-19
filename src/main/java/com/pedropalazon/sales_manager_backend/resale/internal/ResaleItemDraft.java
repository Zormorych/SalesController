package com.pedropalazon.sales_manager_backend.resale.internal;

import java.math.BigDecimal;
import java.time.LocalDate;

// Datos editables de una prenda. Sin purchaseDate se usa hoy; askingPrice (precio anunciado) es opcional
public record ResaleItemDraft(String name, String size, String notes, BigDecimal purchaseCost, LocalDate purchaseDate,
        BigDecimal askingPrice) {
}
