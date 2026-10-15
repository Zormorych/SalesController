package com.pedropalazon.sales_manager_backend.sales.internal;

import com.pedropalazon.sales_manager_backend.sales.SalesOrderApi;
import com.pedropalazon.sales_manager_backend.sales.SalesOrderSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SalesOrderApiService implements SalesOrderApi {

    private final SalesOrderService salesOrderService;

    SalesOrderApiService(SalesOrderService salesOrderService) {
        this.salesOrderService = salesOrderService;
    }

    @Override
    @Transactional(readOnly = true)
    public SalesOrderSnapshot getOrder(Long orderId) {
        SalesOrder order = salesOrderService.find(orderId);
        return new SalesOrderSnapshot(order.getId(), order.getOrderNumber(), order.getCustomerId(),
                order.isCancelled(), order.totalSale());
    }
}
