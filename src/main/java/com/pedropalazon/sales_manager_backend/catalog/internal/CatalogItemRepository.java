package com.pedropalazon.sales_manager_backend.catalog.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface CatalogItemRepository<T extends CatalogItem> extends JpaRepository<T, Long> {

    List<T> findAllByOrderByNameAsc();

    List<T> findAllByActiveTrueOrderByNameAsc();

    Optional<T> findFirstByNameIgnoreCase(String name);
}
