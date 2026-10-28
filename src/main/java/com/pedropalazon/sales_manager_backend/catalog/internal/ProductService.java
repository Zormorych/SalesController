package com.pedropalazon.sales_manager_backend.catalog.internal;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class ProductService extends CatalogItemService<Product> {

    public ProductService(ProductRepository repository) {
        super(repository, "No existe el producto con id %d", "Ya existe un producto llamado '%s'");
    }

    @Override
    protected Product newItem(String name, BigDecimal salePrice, BigDecimal purchaseCost, String description) {
        return new Product(name, salePrice, purchaseCost, description);
    }
}
