package com.pedropalazon.sales_manager_backend.sales.internal;

import com.pedropalazon.sales_manager_backend.customers.CustomerApi;
import com.pedropalazon.sales_manager_backend.customers.CustomerSummary;
import com.pedropalazon.sales_manager_backend.sales.LineStatus;
import com.pedropalazon.sales_manager_backend.sales.OrderLineSnapshot;
import com.pedropalazon.sales_manager_backend.sales.SalesOrderApi;
import com.pedropalazon.sales_manager_backend.sales.SalesOrderSnapshot;
import com.pedropalazon.sales_manager_backend.shared.BusinessRuleException;
import com.pedropalazon.sales_manager_backend.shared.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class SalesOrderApiService implements SalesOrderApi {

    private final SalesOrderService salesOrderService;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository lineRepository;
    private final CustomerApi customerApi;
    private final EntityManager entityManager;

    SalesOrderApiService(SalesOrderService salesOrderService, SalesOrderRepository salesOrderRepository,
            SalesOrderLineRepository lineRepository, CustomerApi customerApi, EntityManager entityManager) {
        this.entityManager = entityManager;
        this.salesOrderService = salesOrderService;
        this.salesOrderRepository = salesOrderRepository;
        this.lineRepository = lineRepository;
        this.customerApi = customerApi;
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

    @Override
    @Transactional(readOnly = true)
    public List<OrderLineSnapshot> findPendingLines() {
        return snapshotsOf(lineRepository.findByStatusInOrdersWithStatus(LineStatus.PENDING, SalesOrderStatus.OPEN));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderLineSnapshot> getLines(Collection<Long> lineIds) {
        return snapshotsOf(findAllOrFail(lineIds));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markLinesOrdered(Collection<Long> lineIds) {
        List<SalesOrderLine> lines = findAllOrFail(lineIds);
        // Bloquea sus pedidos y después recarga su estado (y en cascada el de sus líneas): quien llama puede
        // tener ya estas líneas en memoria, leídas antes de que otra transacción las cancelara
        List<SalesOrder> orders = lines.stream().map(SalesOrderLine::getOrder).distinct().toList();
        salesOrderRepository.lockByIds(orders.stream().map(SalesOrder::getId).toList());
        orders.forEach(entityManager::refresh);
        // Se comprueban todas antes de cambiar ninguna: o se marcan todas o ninguna
        lines.stream()
                .filter(line -> !line.isPending() || line.getOrder().isCancelled())
                .findFirst()
                .ifPresent(line -> {
                    throw new BusinessRuleException(
                            "La línea %d no está pendiente de pedir".formatted(line.getId()));
                });
        lines.forEach(SalesOrderLine::markOrdered);
    }

    private List<SalesOrderLine> findAllOrFail(Collection<Long> lineIds) {
        List<SalesOrderLine> lines = lineRepository.findAllWithOrderByIdIn(lineIds);
        Set<Long> missing = new HashSet<>(lineIds);
        lines.forEach(line -> missing.remove(line.getId()));
        if (!missing.isEmpty()) {
            String ids = missing.stream().sorted().map(String::valueOf).collect(Collectors.joining(", "));
            throw new ResourceNotFoundException("No existen las líneas " + ids);
        }
        return lines;
    }

    private List<OrderLineSnapshot> snapshotsOf(List<SalesOrderLine> lines) {
        Map<Long, CustomerSummary> customers = customerApi.findCustomers(
                lines.stream().map(line -> line.getOrder().getCustomerId()).collect(Collectors.toSet()));
        return lines.stream().map(line -> snapshotOf(line, customers)).toList();
    }

    private static OrderLineSnapshot snapshotOf(SalesOrderLine line, Map<Long, CustomerSummary> customers) {
        SalesOrder order = line.getOrder();
        CustomerSummary customer = customers.get(order.getCustomerId());
        List<OrderLineSnapshot.Customization> customizations = line.getCustomizations().stream()
                .map(customization -> new OrderLineSnapshot.Customization(customization.getCustomizationName(),
                        customization.getCustomText()))
                .toList();
        return new OrderLineSnapshot(line.getId(), order.getId(), order.getOrderNumber(), order.getOrderDate(),
                order.getCustomerId(), customer == null ? null : customer.name(), line.getProductId(),
                line.getProductName(), line.getKitName(), line.getSize(), line.getStatus(), customizations,
                line.totalSale(), line.totalCost());
    }

    private static SalesOrderSnapshot snapshotOf(SalesOrder order) {
        return new SalesOrderSnapshot(order.getId(), order.getOrderNumber(), order.getCustomerId(),
                order.isCancelled(), order.totalSale());
    }
}
