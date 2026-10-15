package com.pedropalazon.sales_manager_backend.catalog.internal.web;

import com.pedropalazon.sales_manager_backend.catalog.internal.Customization;
import com.pedropalazon.sales_manager_backend.catalog.internal.CustomizationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/customizations")
@Tag(name = "Catálogo - Personalizaciones",
        description = "Personalizaciones (nombre y número, parches...) con su precio de venta y su coste")
class CustomizationController extends CatalogItemController<Customization> {

    CustomizationController(CustomizationService service) {
        super(service);
    }
}
