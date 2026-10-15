package com.pedropalazon.sales_manager_backend.purchasing.internal;

import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FactoryBatchRepository extends JpaRepository<FactoryBatch, Long> {

    @Query(value = "select nextval('factory_batch_number_seq')", nativeQuery = true)
    long nextBatchSequence();

    List<FactoryBatch> findAllByOrderByIdDesc();

    // Camisetas que ya están en algún lote (los lotes cancelados liberan las suyas)
    @Query("select l from FactoryBatch b join b.lineIds l")
    Set<Long> findAssignedLineIds();
}
