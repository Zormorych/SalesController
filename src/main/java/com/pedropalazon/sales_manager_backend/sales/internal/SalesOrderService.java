package com.pedropalazon.sales_manager_backend.sales.internal;

import com.pedropalazon.sales_manager_backend.catalog.CatalogApi;
import com.pedropalazon.sales_manager_backend.catalog.CatalogItemSnapshot;
import com.pedropalazon.sales_manager_backend.customers.CustomerApi;
import com.pedropalazon.sales_manager_backend.customers.CustomerSummary;
import com.pedropalazon.sales_manager_backend.shared.BusinessRuleException;
import com.pedropalazon.sales_manager_backend.shared.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesOrderService {

    private final SalesOrderRepository repository;
    private final CatalogApi catalogApi;
    private final CustomerApi customerApi;

    public SalesOrderService(SalesOrderRepository repository, CatalogApi catalogApi, CustomerApi customerApi) {
        this.repository = repository;
        this.catalogApi = catalogApi;
        this.customerApi = customerApi;
    }

    @Transactional
    public SalesOrderView.Details create(Long customerId, LocalDate orderDate, String notes,
            List<OrderLineDraft> lines) {
        CustomerSummary customer = customerApi.getCustomer(customerId);
        LocalDate date = orderDate != null ? orderDate : LocalDate.now();
        String orderNumber = SalesOrder.formatOrderNumber(date.getYear(), repository.nextOrderSequence());
        SalesOrder order = new SalesOrder(orderNumber, customerId, date, notes);
        lines.forEach(draft -> order.addLine(buildLine(draft)));
        repository.save(order);
        return SalesOrderView.details(order, customer);
    }

    @Transactional(readOnly = true)
    public SalesOrderView.Details get(Long id) {
        return detailsOf(find(id));
    }

    @Transactional(readOnly = true)
    public List<SalesOrderView.Summary> list(Long customerId) {
        List<SalesOrder> orders = customerId == null
                ? repository.findAllByOrderByOrderDateDescIdDesc()
                : repository.findByCustomerIdOrderByOrderDateDescIdDesc(customerId);
        Map<Long, CustomerSummary> customers = customerApi.findCustomers(
                orders.stream().map(SalesOrder::getCustomerId).collect(Collectors.toSet()));
        return orders.stream()
                .map(order -> SalesOrderView.summary(order, customerNameOf(customers, order.getCustomerId())))
                .toList();
    }

    @Transactional
    public SalesOrderView.Details updateHeader(Long id, Long customerId, LocalDate orderDate, String notes) {
        SalesOrder order = findOpen(id);
        CustomerSummary customer = customerApi.getCustomer(customerId);
        order.updateHeader(customerId, orderDate, notes);
        return SalesOrderView.details(order, customer);
    }

    @Transactional
    public SalesOrderView.Details addLine(Long id, OrderLineDraft draft) {
        SalesOrder order = findOpen(id);
        order.addLine(buildLine(draft));
        // Inserta ya la línea para que la respuesta lleve su id
        repository.flush();
        return detailsOf(order);
    }

    @Transactional
    public SalesOrderView.Details updateLine(Long id, Long lineId, OrderLineDraft draft) {
        SalesOrder order = findOpen(id);
        SalesOrderLine line = findPendingLine(order, lineId);
        line.replaceWith(buildLine(draft));
        repository.flush();
        return detailsOf(order);
    }

    @Transactional
    public SalesOrderView.Details cancelLine(Long id, Long lineId) {
        SalesOrder order = findOpen(id);
        SalesOrderLine line = findPendingLine(order, lineId);
        if (order.activeLineCount() == 1) {
            throw new BusinessRuleException(
                    "No se puede cancelar la última línea del pedido: cancela el pedido entero");
        }
        line.cancel();
        return detailsOf(order);
    }

    @Transactional
    public SalesOrderView.Details cancel(Long id) {
        SalesOrder order = findOpen(id);
        boolean hasLinesInProgress = order.getLines().stream()
                .anyMatch(line -> !line.isPending() && !line.isCancelled());
        if (hasLinesInProgress) {
            throw new BusinessRuleException("El pedido %s tiene camisetas ya pedidas al fabricante y no se puede cancelar"
                    .formatted(order.getOrderNumber()));
        }
        order.cancel();
        return detailsOf(order);
    }

    private SalesOrder findOpen(Long id) {
        SalesOrder order = find(id);
        if (order.isCancelled()) {
            throw new BusinessRuleException("El pedido %s está cancelado y no se puede modificar"
                    .formatted(order.getOrderNumber()));
        }
        return order;
    }

    private static SalesOrderLine findPendingLine(SalesOrder order, Long lineId) {
        SalesOrderLine line = order.findLine(lineId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe la línea %d en el pedido %s"
                        .formatted(lineId, order.getOrderNumber())));
        if (!line.isPending()) {
            throw new BusinessRuleException("La línea %d ya no está pendiente y no se puede modificar"
                    .formatted(lineId));
        }
        return line;
    }

    private SalesOrderView.Details detailsOf(SalesOrder order) {
        return SalesOrderView.details(order, customerApi.getCustomer(order.getCustomerId()));
    }

    private static String customerNameOf(Map<Long, CustomerSummary> customers, Long customerId) {
        CustomerSummary customer = customers.get(customerId);
        return customer == null ? null : customer.name();
    }

    SalesOrder find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el pedido con id %d".formatted(id)));
    }

    // Copia nombre y precios del catálogo; un precio que llega en la petición (incluido 0) tiene prioridad
    SalesOrderLine buildLine(OrderLineDraft draft) {
        CatalogItemSnapshot product = catalogApi.getActiveProduct(draft.productId());
        SalesOrderLine line = new SalesOrderLine(product.id(), product.name(), draft.kitName(), draft.size(),
                priceOrDefault(draft.unitSalePrice(), product.salePrice()),
                priceOrDefault(draft.unitPurchaseCost(), product.purchaseCost()));
        for (OrderLineDraft.Customization requested : draft.customizations()) {
            CatalogItemSnapshot customization = catalogApi.getActiveCustomization(requested.customizationId());
            line.addCustomization(new LineCustomization(customization.id(), customization.name(),
                    requested.customText(),
                    priceOrDefault(requested.salePrice(), customization.salePrice()),
                    priceOrDefault(requested.purchaseCost(), customization.purchaseCost())));
        }
        return line;
    }

    private static BigDecimal priceOrDefault(BigDecimal requested, BigDecimal catalogPrice) {
        return requested != null ? requested : catalogPrice;
    }
}
