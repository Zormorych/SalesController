package com.pedropalazon.sales_manager_backend.sales.internal;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    // Secuencia de la base de datos: nunca repite número aunque se creen pedidos a la vez
    @Query(value = "select nextval('sales_order_number_seq')", nativeQuery = true)
    long nextOrderSequence();

    // SELECT ... FOR UPDATE: quien llegue después espera a que termine la transacción que tiene el bloqueo
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SalesOrder o where o.id = :id")
    Optional<SalesOrder> findByIdForUpdate(@Param("id") Long id);

    List<SalesOrder> findAllByOrderByOrderDateDescIdDesc();

    List<SalesOrder> findByCustomerIdOrderByOrderDateDescIdDesc(Long customerId);
}
