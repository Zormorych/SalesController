package com.pedropalazon.sales_manager_backend.sales.internal;

import com.pedropalazon.sales_manager_backend.sales.LineStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesOrderLineRepository extends JpaRepository<SalesOrderLine, Long> {

    @Query("""
            select l from SalesOrderLine l join fetch l.order o
            where l.status = :status and o.status = :orderStatus
            order by o.orderDate, o.id, l.id""")
    List<SalesOrderLine> findByStatusInOrdersWithStatus(@Param("status") LineStatus status,
            @Param("orderStatus") SalesOrderStatus orderStatus);

    // Camisetas no canceladas de pedidos con el estado indicado y fecha de pedido en el rango (ambos incluidos)
    @Query("""
            select l from SalesOrderLine l join fetch l.order o
            where l.status <> :excludedStatus and o.status = :orderStatus and o.orderDate between :from and :to
            order by o.orderDate, o.id, l.id""")
    List<SalesOrderLine> findOrderedBetween(@Param("excludedStatus") LineStatus excludedStatus,
            @Param("orderStatus") SalesOrderStatus orderStatus, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select l.status, count(l) from SalesOrderLine l where l.order.status = :orderStatus group by l.status")
    List<Object[]> countByStatusInOrdersWithStatus(@Param("orderStatus") SalesOrderStatus orderStatus);

    @Query("select l from SalesOrderLine l join fetch l.order o where l.id in :ids order by o.orderDate, o.id, l.id")
    List<SalesOrderLine> findAllWithOrderByIdIn(@Param("ids") Collection<Long> ids);
}
