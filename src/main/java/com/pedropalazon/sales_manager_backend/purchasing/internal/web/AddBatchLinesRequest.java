package com.pedropalazon.sales_manager_backend.purchasing.internal.web;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AddBatchLinesRequest(
        @NotEmpty(message = "Indica al menos una camiseta")
        List<@NotNull(message = "La línea no puede estar vacía") Long> lineIds) {
}
