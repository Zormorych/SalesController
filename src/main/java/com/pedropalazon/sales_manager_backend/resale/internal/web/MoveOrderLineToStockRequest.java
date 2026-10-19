package com.pedropalazon.sales_manager_backend.resale.internal.web;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

// customerPays: si el cliente la paga igualmente. Por defecto no: se le devuelve lo que hubiera pagado.
// askingPrice es opcional
public record MoveOrderLineToStockRequest(
        @NotNull(message = "La camiseta (lineId) es obligatoria")
        Long lineId,

        Boolean customerPays,

        @PositiveOrZero(message = "El precio anunciado no puede ser negativo")
        @Digits(integer = 8, fraction = 2, message = "El precio anunciado admite como máximo 8 enteros y 2 decimales")
        BigDecimal askingPrice) {

    boolean customerPaysOrDefault() {
        return customerPays != null && customerPays;
    }
}
