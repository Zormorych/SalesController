package com.pedropalazon.sales_manager_backend.purchasing.internal;

import com.pedropalazon.sales_manager_backend.purchasing.FactoryBatchStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FactoryBatchRepository extends JpaRepository<FactoryBatch, Long> {

    @Query(value = "select nextval('factory_batch_number_seq')", nativeQuery = true)
    long nextBatchSequence();

    // SELECT ... FOR UPDATE: dos operaciones sobre el mismo lote (enviar y añadir camisetas, por ejemplo)
    // se ejecutan una detrás de otra
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from FactoryBatch b where b.id = :id")
    Optional<FactoryBatch> findByIdForUpdate(@Param("id") Long id);

    List<FactoryBatch> findAllByOrderByIdDesc();

    List<FactoryBatch> findByStatusNotOrderByIdDesc(FactoryBatchStatus status);

    // Camisetas que ya están en algún lote (los lotes cancelados liberan las suyas)
    @Query("select l from FactoryBatch b join b.lineIds l")
    Set<Long> findAssignedLineIds();
}
