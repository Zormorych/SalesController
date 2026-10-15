package com.pedropalazon.sales_manager_backend.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

// Usa el H2 en modo PostgreSQL del perfil test, con las migraciones Flyway reales
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductRepositoryTest {

    @Autowired
    private ProductRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void givenNewProduct_whenSaved_thenNamePricesAndTimestampsArePersisted() {
        Product saved = repository.save(new Product("  Camiseta Fan ", new BigDecimal("15"), new BigDecimal("6.5")));
        entityManager.flush();
        entityManager.clear();

        Product found = repository.findById(saved.getId()).orElseThrow();

        assertThat(found.getName()).isEqualTo("Camiseta Fan");
        assertThat(found.getSalePrice()).isEqualTo(new BigDecimal("15.00"));
        assertThat(found.getPurchaseCost()).isEqualTo(new BigDecimal("6.50"));
        assertThat(found.isActive()).isTrue();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void givenActiveAndInactiveProducts_whenFindingActive_thenReturnsOnlyActiveSortedByName() {
        repository.save(new Product("Chándal", new BigDecimal("50.00"), new BigDecimal("34.00")));
        Product inactive = new Product("Cortavientos", new BigDecimal("30.00"), new BigDecimal("18.00"));
        inactive.deactivate();
        repository.save(inactive);
        repository.save(new Product("Camiseta Fan", new BigDecimal("15.00"), new BigDecimal("6.50")));

        assertThat(repository.findAllByActiveTrueOrderByNameAsc())
                .extracting(Product::getName)
                .containsExactly("Camiseta Fan", "Chándal");
        assertThat(repository.findAllByOrderByNameAsc())
                .extracting(Product::getName)
                .containsExactly("Camiseta Fan", "Chándal", "Cortavientos");
    }

    @Test
    void givenExistingProduct_whenFindingByNameIgnoringCase_thenFindsItOnlyByThatName() {
        Product product = repository.save(new Product("Camiseta Fan", new BigDecimal("15.00"), new BigDecimal("6.50")));

        assertThat(repository.findFirstByNameIgnoreCase("CAMISETA FAN")).map(Product::getId).contains(product.getId());
        assertThat(repository.findFirstByNameIgnoreCase("Camiseta Player")).isEmpty();
    }
}
