package com.pedropalazon.sales_manager_backend.sales.internal.web;

import com.pedropalazon.sales_manager_backend.sales.internal.SalesOrderService;
import com.pedropalazon.sales_manager_backend.sales.internal.SalesOrderView;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/sales/orders")
@Tag(name = "Ventas - Pedidos", description = "Pedidos de cliente con sus camisetas, personalizaciones y totales")
class SalesOrderController {

    private final SalesOrderService service;

    SalesOrderController(SalesOrderService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<SalesOrderView.Details> create(@Valid @RequestBody CreateSalesOrderRequest request) {
        SalesOrderView.Details order = service.create(request.customerId(), request.orderDate(), request.notes(),
                request.lineDrafts());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(order.id())
                .toUri();
        return ResponseEntity.created(location).body(order);
    }

    @GetMapping("/{id}")
    SalesOrderView.Details get(@PathVariable Long id) {
        return service.get(id);
    }
}
