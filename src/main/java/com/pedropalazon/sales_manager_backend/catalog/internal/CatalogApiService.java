package com.pedropalazon.sales_manager_backend.catalog.internal;

import com.pedropalazon.sales_manager_backend.catalog.CatalogApi;
import com.pedropalazon.sales_manager_backend.catalog.CatalogItemSnapshot;
import com.pedropalazon.sales_manager_backend.shared.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CatalogApiService implements CatalogApi {

    private final ProductService productService;
    private final CustomizationService customizationService;

    CatalogApiService(ProductService productService, CustomizationService customizationService) {
        this.productService = productService;
        this.customizationService = customizationService;
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogItemSnapshot getActiveProduct(Long productId) {
        Product product = productService.get(productId);
        if (!product.isActive()) {
            throw new BusinessRuleException("El producto '%s' está desactivado".formatted(product.getName()));
        }
        return snapshotOf(product);
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogItemSnapshot getActiveCustomization(Long customizationId) {
        Customization customization = customizationService.get(customizationId);
        if (!customization.isActive()) {
            throw new BusinessRuleException(
                    "La personalización '%s' está desactivada".formatted(customization.getName()));
        }
        return snapshotOf(customization);
    }

    private static CatalogItemSnapshot snapshotOf(CatalogItem item) {
        return new CatalogItemSnapshot(item.getId(), item.getName(), item.getSalePrice(), item.getPurchaseCost());
    }
}
