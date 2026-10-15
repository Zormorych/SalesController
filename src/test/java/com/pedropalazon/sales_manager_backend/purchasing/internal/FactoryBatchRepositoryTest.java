package com.pedropalazon.sales_manager_backend.purchasing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FactoryBatchRepositoryTest {

    // Filas de otros módulos que exigen las claves foráneas: un pedido con dos líneas
    private static final long FIRST_LINE_ID = 9001L;
    private static final long SECOND_LINE_ID = 9002L;

    @Autowired
    private FactoryBatchRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void insertOrderLines() {
        var em = entityManager.getEntityManager();
        em.createNativeQuery("insert into customers (id, name, created_at, updated_at) "
                + "values (9001, 'Ana Pérez', current_timestamp, current_timestamp)").executeUpdate();
        em.createNativeQuery("insert into products (id, name, sale_price, purchase_cost, active, created_at, updated_at) "
                + "values (9001, 'Camiseta Fan', 15.00, 6.50, true, current_timestamp, current_timestamp)").executeUpdate();
        em.createNativeQuery("insert into sales_orders (id, order_number, customer_id, order_date, status, created_at, updated_at) "
                + "values (9001, 'SO-2026-009001', 9001, date '2026-10-15', 'OPEN', current_timestamp, current_timestamp)")
                .executeUpdate();
        for (long lineId : List.of(FIRST_LINE_ID, SECOND_LINE_ID)) {
            em.createNativeQuery("insert into sales_order_lines (id, sales_order_id, product_id, product_name, kit_name, "
                    + "clothing_size, unit_sale_price, unit_purchase_cost, status) values (" + lineId
                    + ", 9001, 9001, 'Camiseta Fan', 'España', 'M', 15.00, 6.50, 'PENDING')").executeUpdate();
        }
    }

    @Test
    void givenBatchWithLines_whenSavedAndReloaded_thenKeepsLinesShippingAndAssignedIds() {
        FactoryBatch batch = new FactoryBatch("FB-2026-0001", new BigDecimal("4"), "Pedido de octubre");
        batch.addLines(List.of(FIRST_LINE_ID, SECOND_LINE_ID));
        Long id = repository.save(batch).getId();
        entityManager.flush();
        entityManager.clear();

        FactoryBatch found = repository.findById(id).orElseThrow();

        assertThat(found.getBatchNumber()).isEqualTo("FB-2026-0001");
        assertThat(found.getStatus()).isEqualTo(FactoryBatchStatus.OPEN);
        assertThat(found.getShippingCost()).isEqualTo(new BigDecimal("4.00"));
        assertThat(found.getLineIds()).containsExactlyInAnyOrder(FIRST_LINE_ID, SECOND_LINE_ID);
        assertThat(repository.findAssignedLineIds()).containsExactlyInAnyOrder(FIRST_LINE_ID, SECOND_LINE_ID);
    }

    @Test
    void givenLineAlreadyInABatch_whenAddedToAnotherBatch_thenDatabaseRejectsIt() {
        FactoryBatch first = new FactoryBatch("FB-2026-0001", new BigDecimal("4.00"), null);
        first.addLines(List.of(FIRST_LINE_ID));
        repository.saveAndFlush(first);

        FactoryBatch second = new FactoryBatch("FB-2026-0002", new BigDecimal("4.00"), null);
        second.addLines(List.of(FIRST_LINE_ID));

        assertThatThrownBy(() -> repository.saveAndFlush(second)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void givenSequence_whenFormattingBatchNumber_thenUsesYearAndFourDigits() {
        long sequence = repository.nextBatchSequence();

        assertThat(FactoryBatch.formatBatchNumber(2026, sequence)).matches("FB-2026-[0-9]{4,}");
        assertThat(FactoryBatch.formatBatchNumber(2026, 7)).isEqualTo("FB-2026-0007");
    }
}
