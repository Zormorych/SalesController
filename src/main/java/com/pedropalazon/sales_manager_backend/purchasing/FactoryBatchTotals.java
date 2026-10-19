package com.pedropalazon.sales_manager_backend.purchasing;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Cifras de un lote: solo cuentan sus camisetas no canceladas. profit = sale - cost - shipping
public record FactoryBatchTotals(Long batchId, String batchNumber, FactoryBatchStatus status, LocalDateTime sentAt,
        LocalDateTime receivedAt, long shirts, BigDecimal sale, BigDecimal cost, BigDecimal shipping,
        BigDecimal profit) {
}
