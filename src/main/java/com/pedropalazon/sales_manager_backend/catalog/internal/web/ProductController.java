package com.pedropalazon.sales_manager_backend.catalog.internal.web;

import com.pedropalazon.sales_manager_backend.catalog.internal.Product;
import com.pedropalazon.sales_manager_backend.catalog.internal.ProductService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/products")
@Tag(name = "Catálogo - Productos", description = "Productos que se venden, con su precio de venta y su coste")
class ProductController extends CatalogItemController<Product> {

    ProductController(ProductService service) {
        super(service);
    }
}
