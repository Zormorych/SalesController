package com.pedropalazon.sales_manager_backend.payments.internal.web;

import com.pedropalazon.sales_manager_backend.payments.internal.PaymentService;
import com.pedropalazon.sales_manager_backend.payments.internal.PaymentSummary;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Vive en el módulo de cobros aunque la ruta cuelgue del pedido: ventas no conoce a cobros
@RestController
@RequestMapping("/api/sales/orders/{orderId}/payments")
@Tag(name = "Ventas - Cobros", description = "Cobros de un pedido: lo cobrado y lo que queda pendiente")
class PaymentController {

    private final PaymentService service;

    PaymentController(PaymentService service) {
        this.service = service;
    }

    @GetMapping
    PaymentSummary summary(@PathVariable Long orderId) {
        return service.summary(orderId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PaymentSummary register(@PathVariable Long orderId, @Valid @RequestBody RegisterPaymentRequest request) {
        return service.register(orderId, request.amount(), request.paymentDate(), request.notes());
    }
}
