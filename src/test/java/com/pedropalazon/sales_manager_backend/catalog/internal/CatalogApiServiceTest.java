package com.pedropalazon.sales_manager_backend.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pedropalazon.sales_manager_backend.catalog.CatalogApi;
import com.pedropalazon.sales_manager_backend.catalog.CatalogItemSnapshot;
import com.pedropalazon.sales_manager_backend.shared.BusinessRuleException;
import com.pedropalazon.sales_manager_backend.shared.ResourceNotFoundException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CatalogApiServiceTest {

    @Autowired
    private CatalogApi catalogApi;

    @Autowired
    private ProductService productService;

    @Autowired
    private CustomizationService customizationService;

    @Test
    void givenActiveProduct_whenRequested_thenReturnsSnapshotWithPrices() {
        Product product = productService.create("Camiseta Fan", new BigDecimal("15.00"), new BigDecimal("6.50"));

        CatalogItemSnapshot snapshot = catalogApi.getActiveProduct(product.getId());

        assertThat(snapshot.id()).isEqualTo(product.getId());
        assertThat(snapshot.name()).isEqualTo("Camiseta Fan");
        assertThat(snapshot.salePrice()).isEqualByComparingTo("15.00");
        assertThat(snapshot.purchaseCost()).isEqualByComparingTo("6.50");
    }

    @Test
    void givenDeactivatedProduct_whenRequested_thenThrowsBusinessRule() {
        Product product = productService.create("Camiseta Fan", new BigDecimal("15.00"), new BigDecimal("6.50"));
        productService.deactivate(product.getId());

        assertThatThrownBy(() -> catalogApi.getActiveProduct(product.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("El producto 'Camiseta Fan' está desactivado");
    }

    @Test
    void givenUnknownProduct_whenRequested_thenThrowsNotFound() {
        assertThatThrownBy(() -> catalogApi.getActiveProduct(999999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No existe el producto con id 999999");
    }

    @Test
    void givenActiveCustomization_whenRequested_thenReturnsSnapshot() {
        Customization customization = customizationService.create("Nombre + número", new BigDecimal("2.00"),
                new BigDecimal("1.20"));

        CatalogItemSnapshot snapshot = catalogApi.getActiveCustomization(customization.getId());

        assertThat(snapshot.name()).isEqualTo("Nombre + número");
        assertThat(snapshot.salePrice()).isEqualByComparingTo("2.00");
    }

    @Test
    void givenDeactivatedCustomization_whenRequested_thenThrowsBusinessRule() {
        Customization customization = customizationService.create("Parche", new BigDecimal("1.00"),
                new BigDecimal("0.50"));
        customizationService.deactivate(customization.getId());

        assertThatThrownBy(() -> catalogApi.getActiveCustomization(customization.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("La personalización 'Parche' está desactivada");
    }
}
