package com.pedropalazon.sales_manager_backend.sales.internal.web;

import com.pedropalazon.sales_manager_backend.sales.internal.SalesOrderService;
import com.pedropalazon.sales_manager_backend.sales.internal.SalesOrderView;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    @GetMapping
    List<SalesOrderView.Summary> list(@RequestParam(required = false) Long customerId) {
        return service.list(customerId);
    }

    @GetMapping("/{id}")
    SalesOrderView.Details get(@PathVariable Long id) {
        return service.get(id);
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

    @PutMapping("/{id}")
    SalesOrderView.Details updateHeader(@PathVariable Long id, @Valid @RequestBody UpdateSalesOrderRequest request) {
        return service.updateHeader(id, request.customerId(), request.orderDate(), request.notes());
    }

    @PostMapping("/{id}/cancel")
    SalesOrderView.Details cancel(@PathVariable Long id) {
        return service.cancel(id);
    }

    @PostMapping("/{id}/lines")
    @ResponseStatus(HttpStatus.CREATED)
    SalesOrderView.Details addLine(@PathVariable Long id, @Valid @RequestBody OrderLineRequest request) {
        return service.addLine(id, request.toDraft());
    }

    @PutMapping("/{id}/lines/{lineId}")
    SalesOrderView.Details updateLine(@PathVariable Long id, @PathVariable Long lineId,
            @Valid @RequestBody OrderLineRequest request) {
        return service.updateLine(id, lineId, request.toDraft());
    }

    @PostMapping("/{id}/deliver")
    SalesOrderView.Details deliverAll(@PathVariable Long id) {
        return service.deliverAll(id);
    }

    @PostMapping("/{id}/lines/{lineId}/deliver")
    SalesOrderView.Details deliverLine(@PathVariable Long id, @PathVariable Long lineId) {
        return service.deliverLine(id, lineId);
    }

    @PostMapping("/{id}/lines/{lineId}/cancel")
    SalesOrderView.Details cancelLine(@PathVariable Long id, @PathVariable Long lineId) {
        return service.cancelLine(id, lineId);
    }
}
