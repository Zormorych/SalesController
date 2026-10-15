package com.pedropalazon.sales_manager_backend.sales.internal;

import com.pedropalazon.sales_manager_backend.sales.SalesOrderApi;
import com.pedropalazon.sales_manager_backend.sales.SalesOrderSnapshot;
import com.pedropalazon.sales_manager_backend.shared.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class SalesOrderApiService implements SalesOrderApi {

    private final SalesOrderService salesOrderService;
    private final SalesOrderRepository salesOrderRepository;

    SalesOrderApiService(SalesOrderService salesOrderService, SalesOrderRepository salesOrderRepository) {
        this.salesOrderService = salesOrderService;
        this.salesOrderRepository = salesOrderRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public SalesOrderSnapshot getOrder(Long orderId) {
        return snapshotOf(salesOrderService.find(orderId));
    }

    // MANDATORY: el bloqueo solo tiene sentido dentro de la transacción de quien llama, que es
    // la que lo mantiene hasta hacer commit
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public SalesOrderSnapshot lockOrder(Long orderId) {
        SalesOrder order = salesOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el pedido con id %d".formatted(orderId)));
        return snapshotOf(order);
    }

    private static SalesOrderSnapshot snapshotOf(SalesOrder order) {
        return new SalesOrderSnapshot(order.getId(), order.getOrderNumber(), order.getCustomerId(),
                order.isCancelled(), order.totalSale());
    }
}
