package com.pedropalazon.sales_manager_backend.payments;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

// API pública del módulo de cobros: la usan los informes. Los cobros anulados nunca cuentan.
public interface PaymentApi {

    // Total cobrado de cada pedido (solo aparecen los pedidos con algún cobro válido)
    Map<Long, BigDecimal> paidAmountsByOrder();

    // Total cobrado con fecha de cobro entre from y to (ambas incluidas)
    BigDecimal collectedBetween(LocalDate from, LocalDate to);
}
