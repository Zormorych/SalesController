package com.pedropalazon.sales_manager_backend.sales.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.pedropalazon.sales_manager_backend.catalog.ClothingSize;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SalesOrderRepositoryTest {

    // Ids fijos para las filas de otros módulos que exigen las claves foráneas
    private static final long CUSTOMER_ID = 9001L;
    private static final long PRODUCT_ID = 9001L;
    private static final long CUSTOMIZATION_ID = 9001L;

    @Autowired
    private SalesOrderRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void insertReferencedRows() {
        var em = entityManager.getEntityManager();
        em.createNativeQuery("insert into customers (id, name, created_at, updated_at) "
                + "values (" + CUSTOMER_ID + ", 'Ana Pérez', current_timestamp, current_timestamp)").executeUpdate();
        em.createNativeQuery("insert into products (id, name, sale_price, purchase_cost, active, created_at, updated_at) "
                + "values (" + PRODUCT_ID + ", 'Camiseta Fan', 15.00, 6.50, true, current_timestamp, current_timestamp)")
                .executeUpdate();
        em.createNativeQuery("insert into customizations (id, name, sale_price, purchase_cost, active, created_at, updated_at) "
                + "values (" + CUSTOMIZATION_ID + ", 'Nombre + número', 2.00, 1.20, true, current_timestamp, current_timestamp)")
                .executeUpdate();
    }

    @Test
    void givenOrderWithLineAndCustomization_whenSavedAndReloaded_thenKeepsSnapshotsAndSize() {
        SalesOrder order = new SalesOrder("SO-2026-000001", CUSTOMER_ID, LocalDate.of(2026, 10, 15), "Entregar en mano");
        SalesOrderLine line = new SalesOrderLine(PRODUCT_ID, "Camiseta Fan", "Selección Española blanca 2 estrellas",
                ClothingSize.KIDS_24, new BigDecimal("15.00"), new BigDecimal("6.50"));
        line.addCustomization(new LineCustomization(CUSTOMIZATION_ID, "Nombre + número", "WILLIAMS JR 17",
                new BigDecimal("0.00"), new BigDecimal("1.20")));
        order.addLine(line);
        Long id = repository.save(order).getId();
        entityManager.flush();
        entityManager.clear();

        SalesOrder found = repository.findById(id).orElseThrow();

        assertThat(found.getOrderNumber()).isEqualTo("SO-2026-000001");
        assertThat(found.getStatus()).isEqualTo(SalesOrderStatus.OPEN);
        assertThat(found.getLines()).hasSize(1);
        SalesOrderLine foundLine = found.getLines().getFirst();
        assertThat(foundLine.getSize()).isEqualTo(ClothingSize.KIDS_24);
        assertThat(foundLine.getStatus()).isEqualTo(LineStatus.PENDING);
        assertThat(foundLine.getCustomizations()).singleElement()
                .satisfies(customization -> {
                    assertThat(customization.getCustomText()).isEqualTo("WILLIAMS JR 17");
                    assertThat(customization.getSalePrice()).isEqualTo(new BigDecimal("0.00"));
                });
        assertThat(found.totalSale()).isEqualTo(new BigDecimal("15.00"));
    }

    @Test
    void givenSequence_whenAskingTwice_thenNumbersIncrease() {
        long first = repository.nextOrderSequence();
        long second = repository.nextOrderSequence();

        assertThat(second).isGreaterThan(first);
    }
}
