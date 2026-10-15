package com.pedropalazon.sales_manager_backend.sales.internal.web;

import com.pedropalazon.sales_manager_backend.sales.internal.OrderLineDraft;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

// orderDate es opcional: si no viene, el pedido se fecha hoy
public record CreateSalesOrderRequest(
        @NotNull(message = "El cliente es obligatorio")
        Long customerId,

        LocalDate orderDate,

        @Size(max = 500, message = "Las notas no pueden superar 500 caracteres")
        String notes,

        @NotEmpty(message = "El pedido necesita al menos una línea")
        @Valid
        List<OrderLineRequest> lines) {

    List<OrderLineDraft> lineDrafts() {
        return lines.stream().map(OrderLineRequest::toDraft).toList();
    }
}
