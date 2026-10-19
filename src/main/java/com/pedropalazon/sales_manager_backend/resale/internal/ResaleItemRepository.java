package com.pedropalazon.sales_manager_backend.resale.internal;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResaleItemRepository extends JpaRepository<ResaleItem, Long> {

    List<ResaleItem> findAllByOrderByIdDesc();

    List<ResaleItem> findByStatusOrderByIdDesc(ResaleItemStatus status);

    // Una sola fila [número, total vendido, total coste]
    @Query("""
            select count(i), coalesce(sum(i.salePrice), 0), coalesce(sum(i.purchaseCost), 0) from ResaleItem i
            where i.status = :status and i.saleDate between :from and :to""")
    List<Object[]> sumSoldBetween(@Param("status") ResaleItemStatus status, @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    // Una sola fila [número, total coste]
    @Query("select count(i), coalesce(sum(i.purchaseCost), 0) from ResaleItem i where i.status = :status")
    List<Object[]> sumWithStatus(@Param("status") ResaleItemStatus status);
}
