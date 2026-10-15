package com.pedropalazon.sales_manager_backend.purchasing.internal.web;

import com.pedropalazon.sales_manager_backend.purchasing.internal.FactoryBatchService;
import com.pedropalazon.sales_manager_backend.purchasing.internal.FactoryBatchView;
import com.pedropalazon.sales_manager_backend.sales.OrderLineSnapshot;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/purchasing")
@Tag(name = "Compras - Lotes al fabricante",
        description = "Camisetas pendientes de pedir y lotes que se envían al fabricante, con su coste de envío")
class FactoryBatchController {

    private final FactoryBatchService service;

    FactoryBatchController(FactoryBatchService service) {
        this.service = service;
    }

    @GetMapping("/pending-lines")
    List<OrderLineSnapshot> pendingLines() {
        return service.pendingLines();
    }

    @GetMapping("/batches")
    List<FactoryBatchView.Summary> list() {
        return service.list();
    }

    @GetMapping("/batches/{id}")
    FactoryBatchView.Details get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/batches")
    ResponseEntity<FactoryBatchView.Details> create(@Valid @RequestBody CreateFactoryBatchRequest request) {
        FactoryBatchView.Details batch = service.create(request.lineIds(), request.shippingCost(), request.notes());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(batch.id())
                .toUri();
        return ResponseEntity.created(location).body(batch);
    }

    @PutMapping("/batches/{id}")
    FactoryBatchView.Details update(@PathVariable Long id, @Valid @RequestBody UpdateFactoryBatchRequest request) {
        return service.updateDetails(id, request.shippingCost(), request.notes());
    }
}
