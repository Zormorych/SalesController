package com.pedropalazon.sales_manager_backend.purchasing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

// API pública del módulo de compras: la usan los informes. Los lotes descartados no cuentan.
public interface PurchasingApi {

    // Parte del envío que corresponde a cada camiseta de un lote (las que no están en ninguno no aparecen)
    Map<Long, BigDecimal> shippingSharesByLine();

    // Totales de cada lote, del más reciente al más antiguo
    List<FactoryBatchTotals> findBatchTotals();

    // Parte del envío de una camiseta en su lote (0,00 si no está en ninguno). Una camiseta pasada a stock la conserva
    BigDecimal shippingShareOf(Long lineId);
}
