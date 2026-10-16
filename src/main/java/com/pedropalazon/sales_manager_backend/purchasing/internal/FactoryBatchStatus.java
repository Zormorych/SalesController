package com.pedropalazon.sales_manager_backend.purchasing.internal;

// OPEN → SENT → RECEIVED; un lote abierto también se puede descartar (CANCELLED)
public enum FactoryBatchStatus {
    OPEN,
    SENT,
    RECEIVED,
    CANCELLED
}
