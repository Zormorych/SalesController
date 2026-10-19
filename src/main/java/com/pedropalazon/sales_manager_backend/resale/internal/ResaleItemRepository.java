package com.pedropalazon.sales_manager_backend.resale.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResaleItemRepository extends JpaRepository<ResaleItem, Long> {

    List<ResaleItem> findAllByOrderByIdDesc();

    List<ResaleItem> findByStatusOrderByIdDesc(ResaleItemStatus status);
}
