package com.pedropalazon.sales_manager_backend.purchasing;

// Estado de un lote al fabricante. Es API pública: los informes lo leen.
// OPEN → SENT → RECEIVED; un lote abierto también se puede descartar (CANCELLED)
public enum FactoryBatchStatus {
    OPEN,
    SENT,
    RECEIVED,
    CANCELLED
}
