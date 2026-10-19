package com.pedropalazon.sales_manager_backend.resale.internal.web;

import com.pedropalazon.sales_manager_backend.resale.internal.ResaleItemService;
import com.pedropalazon.sales_manager_backend.resale.internal.ResaleItemStatus;
import com.pedropalazon.sales_manager_backend.resale.internal.ResaleItemView;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resale/items")
@Tag(name = "Stock y reventa", description = "Prendas para revender: alta con su coste, venta con el precio final y beneficio")
class ResaleItemController {

    private final ResaleItemService service;

    ResaleItemController(ResaleItemService service) {
        this.service = service;
    }

    @GetMapping
    List<ResaleItemView> list(@RequestParam(required = false) ResaleItemStatus status) {
        return service.list(status);
    }

    @GetMapping("/{id}")
    ResaleItemView get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ResaleItemView create(@Valid @RequestBody ResaleItemRequest request) {
        return service.create(request.toDraft());
    }

    // Camiseta recibida que el cliente ya no quiere
    @PostMapping("/from-order-line")
    @ResponseStatus(HttpStatus.CREATED)
    ResaleItemView moveFromOrderLine(@Valid @RequestBody MoveOrderLineToStockRequest request) {
        return service.moveFromOrderLine(request.lineId(), request.customerPaysOrDefault(), request.askingPrice());
    }

    @PutMapping("/{id}")
    ResaleItemView update(@PathVariable Long id, @Valid @RequestBody ResaleItemRequest request) {
        return service.update(id, request.toDraft());
    }

    @PostMapping("/{id}/sell")
    ResaleItemView sell(@PathVariable Long id, @Valid @RequestBody SellResaleItemRequest request) {
        return service.sell(id, request.salePrice(), request.saleDate());
    }

    @PostMapping("/{id}/unsell")
    ResaleItemView unsell(@PathVariable Long id) {
        return service.unsell(id);
    }
}
