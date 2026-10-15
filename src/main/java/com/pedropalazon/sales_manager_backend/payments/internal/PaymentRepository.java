package com.pedropalazon.sales_manager_backend.payments.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findBySalesOrderIdOrderByPaymentDateAscIdAsc(Long salesOrderId);
}
