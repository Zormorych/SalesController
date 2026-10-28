package com.pedropalazon.sales_manager_backend.catalog.internal;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class CustomizationService extends CatalogItemService<Customization> {

    public CustomizationService(CustomizationRepository repository) {
        super(repository, "No existe la personalización con id %d", "Ya existe una personalización llamada '%s'");
    }

    @Override
    protected Customization newItem(String name, BigDecimal salePrice, BigDecimal purchaseCost, String description) {
        return new Customization(name, salePrice, purchaseCost, description);
    }
}
