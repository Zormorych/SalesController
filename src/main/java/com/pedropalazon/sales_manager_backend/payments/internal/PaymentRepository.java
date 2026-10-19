package com.pedropalazon.sales_manager_backend.payments.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findBySalesOrderIdOrderByPaymentDateAscIdAsc(Long salesOrderId);

    // Filas [id del pedido, total cobrado]
    @Query("select p.salesOrderId, sum(p.amount) from Payment p where p.status <> :excluded group by p.salesOrderId")
    List<Object[]> sumByOrderExcludingStatus(@Param("excluded") PaymentStatus excluded);

    @Query("select coalesce(sum(p.amount), 0) from Payment p "
            + "where p.status <> :excluded and p.paymentDate between :from and :to")
    BigDecimal sumBetweenExcludingStatus(@Param("excluded") PaymentStatus excluded, @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
