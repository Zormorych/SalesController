package com.pedropalazon.sales_manager_backend.payments.internal.web;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

// paymentDate es opcional: si no viene, el cobro se fecha hoy
public record RegisterPaymentRequest(
        @NotNull(message = "El importe es obligatorio")
        @Positive(message = "El importe debe ser mayor que 0")
        @Digits(integer = 8, fraction = 2, message = "El importe admite como máximo 8 enteros y 2 decimales")
        BigDecimal amount,

        LocalDate paymentDate,

        @Size(max = 500, message = "Las notas no pueden superar 500 caracteres")
        String notes) {
}
