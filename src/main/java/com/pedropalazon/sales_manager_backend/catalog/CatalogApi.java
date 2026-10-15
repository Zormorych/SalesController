package com.pedropalazon.sales_manager_backend.catalog;

// API pública del catálogo para otros módulos (por ejemplo, ventas copia de aquí nombre y precios).
public interface CatalogApi {

    // Lanza ResourceNotFoundException si no existe y BusinessRuleException si está desactivado
    CatalogItemSnapshot getActiveProduct(Long productId);

    // Lanza ResourceNotFoundException si no existe y BusinessRuleException si está desactivada
    CatalogItemSnapshot getActiveCustomization(Long customizationId);
}
