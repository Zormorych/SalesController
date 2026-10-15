package com.pedropalazon.sales_manager_backend.sales.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    // Secuencia de la base de datos: nunca repite número aunque se creen pedidos a la vez
    @Query(value = "select nextval('sales_order_number_seq')", nativeQuery = true)
    long nextOrderSequence();

    List<SalesOrder> findAllByOrderByOrderDateDescIdDesc();

    List<SalesOrder> findByCustomerIdOrderByOrderDateDescIdDesc(Long customerId);
}
