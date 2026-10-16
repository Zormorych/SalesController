package com.pedropalazon.sales_manager_backend.purchasing.internal.web;

import jakarta.validation.constraints.NotNull;
import java.util.List;

// Sin missingLineIds (o vacío) = ha llegado todo
public record ReceiveFactoryBatchRequest(
        List<@NotNull(message = "La línea no puede estar vacía") Long> missingLineIds) {

    List<Long> missingOrEmpty() {
        return missingLineIds == null ? List.of() : missingLineIds;
    }
}
