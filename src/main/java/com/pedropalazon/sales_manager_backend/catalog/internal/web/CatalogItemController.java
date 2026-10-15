package com.pedropalazon.sales_manager_backend.catalog.internal.web;

import com.pedropalazon.sales_manager_backend.catalog.internal.CatalogItem;
import com.pedropalazon.sales_manager_backend.catalog.internal.CatalogItemService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

// Endpoints comunes; cada subclase los monta en su propia ruta con @RequestMapping.
abstract class CatalogItemController<T extends CatalogItem> {

    private final CatalogItemService<T> service;

    protected CatalogItemController(CatalogItemService<T> service) {
        this.service = service;
    }

    @GetMapping
    List<CatalogItemResponse> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.list(includeInactive).stream().map(CatalogItemResponse::from).toList();
    }

    @GetMapping("/{id}")
    CatalogItemResponse get(@PathVariable Long id) {
        return CatalogItemResponse.from(service.get(id));
    }

    @PostMapping
    ResponseEntity<CatalogItemResponse> create(@Valid @RequestBody CatalogItemRequest request) {
        T item = service.create(request.name(), request.salePrice(), request.purchaseCost());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(item.getId())
                .toUri();
        return ResponseEntity.created(location).body(CatalogItemResponse.from(item));
    }

    @PutMapping("/{id}")
    CatalogItemResponse update(@PathVariable Long id, @Valid @RequestBody CatalogItemRequest request) {
        return CatalogItemResponse.from(
                service.update(id, request.name(), request.salePrice(), request.purchaseCost()));
    }

    @PostMapping("/{id}/activate")
    CatalogItemResponse activate(@PathVariable Long id) {
        return CatalogItemResponse.from(service.activate(id));
    }

    @PostMapping("/{id}/deactivate")
    CatalogItemResponse deactivate(@PathVariable Long id) {
        return CatalogItemResponse.from(service.deactivate(id));
    }
}
