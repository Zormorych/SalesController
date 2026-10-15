package com.pedropalazon.sales_manager_backend.payments.internal;

import static org.assertj.core.api.Assertions.assertThat;

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
class PaymentRepositoryTest {

    // Filas de otros módulos que exige la clave foránea del pago
    private static final long ORDER_ID = 9001L;

    @Autowired
    private PaymentRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void insertOrder() {
        var em = entityManager.getEntityManager();
        em.createNativeQuery("insert into customers (id, name, created_at, updated_at) "
                + "values (9001, 'Ana Pérez', current_timestamp, current_timestamp)").executeUpdate();
        em.createNativeQuery("insert into sales_orders (id, order_number, customer_id, order_date, status, created_at, updated_at) "
                + "values (" + ORDER_ID + ", 'SO-2026-009001', 9001, date '2026-10-15', 'OPEN', current_timestamp, current_timestamp)")
                .executeUpdate();
    }

    @Test
    void givenPayments_whenSavedAndReloaded_thenAreSortedByDateWithAmountAndNotes() {
        repository.save(new Payment(ORDER_ID, new BigDecimal("7"), LocalDate.of(2026, 10, 20), "Bizum"));
        repository.save(new Payment(ORDER_ID, new BigDecimal("10.00"), LocalDate.of(2026, 10, 15), "  "));
        entityManager.flush();
        entityManager.clear();

        var payments = repository.findBySalesOrderIdOrderByPaymentDateAscIdAsc(ORDER_ID);

        assertThat(payments).extracting(Payment::getPaymentDate)
                .containsExactly(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 20));
        assertThat(payments.get(0).getAmount()).isEqualTo(new BigDecimal("10.00"));
        assertThat(payments.get(0).getNotes()).isNull();
        assertThat(payments.get(1).getAmount()).isEqualTo(new BigDecimal("7.00"));
        assertThat(payments.get(1).getNotes()).isEqualTo("Bizum");
        assertThat(payments.get(1).getStatus()).isEqualTo(PaymentStatus.REGISTERED);
    }

    @Test
    void givenPayment_whenVoidedAndReloaded_thenStatusIsPersisted() {
        Long id = repository.save(new Payment(ORDER_ID, new BigDecimal("10.00"), LocalDate.of(2026, 10, 15), null)).getId();
        entityManager.flush();
        entityManager.clear();

        repository.findById(id).orElseThrow().voidPayment();
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findById(id).orElseThrow().isVoided()).isTrue();
    }
}
