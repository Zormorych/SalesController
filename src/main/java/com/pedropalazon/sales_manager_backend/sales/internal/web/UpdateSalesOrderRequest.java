package com.pedropalazon.sales_manager_backend.sales.internal.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateSalesOrderRequest(
        Long customerId,

        @NotNull(message = "La fecha del pedido es obligatoria")
        LocalDate orderDate,

        @Size(max = 500, message = "Las notas no pueden superar 500 caracteres")
        String notes) {
}
