package com.pedropalazon.sales_manager_backend.sales.internal;

import com.pedropalazon.sales_manager_backend.catalog.CatalogApi;
import com.pedropalazon.sales_manager_backend.catalog.CatalogItemSnapshot;
import com.pedropalazon.sales_manager_backend.customers.CustomerApi;
import com.pedropalazon.sales_manager_backend.customers.CustomerSummary;
import com.pedropalazon.sales_manager_backend.sales.LineStatus;
import com.pedropalazon.sales_manager_backend.shared.BusinessRuleException;
import com.pedropalazon.sales_manager_backend.shared.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        CustomerSummary customer = customerOf(customerId);
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
        CustomerSummary customer = customerOf(customerId);
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
        line.replaceWith(buildLine(draft, line));
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
        // Una camiseta ya pedida al fabricante se fabrica y se paga igual: cancelarla falsearía el coste
        // y el envío de su lote. Si el cliente ya no la quiere, cuando llega se pasa a stock.
        boolean hasShirtsAlreadyOrdered = order.getLines().stream()
                .anyMatch(line -> !line.isPending() && line.countsAsSale());
        if (hasShirtsAlreadyOrdered) {
            throw new BusinessRuleException("El pedido %s tiene camisetas ya pedidas al fabricante y no se puede cancelar"
                    .formatted(order.getOrderNumber()));
        }
        order.cancel();
        return detailsOf(order);
    }

    // Toda modificación bloquea el pedido hasta el commit: así no se pisa con un cobro o con el envío
    // de un lote al fabricante que toquen el mismo pedido a la vez
    private SalesOrder findOpen(Long id) {
        SalesOrder order = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el pedido con id %d".formatted(id)));
        if (order.isCancelled()) {
            throw new BusinessRuleException("El pedido %s está cancelado y no se puede modificar"
                    .formatted(order.getOrderNumber()));
        }
        return order;
    }

    // Entrega al cliente una camiseta que ya ha llegado del fabricante
    @Transactional
    public SalesOrderView.Details deliverLine(Long id, Long lineId) {
        SalesOrder order = findOpen(id);
        SalesOrderLine line = findLine(order, lineId);
        if (line.getStatus() == LineStatus.DELIVERED) {
            throw new BusinessRuleException("La línea %d ya está entregada".formatted(lineId));
        }
        if (line.isCancelled()) {
            throw new BusinessRuleException("La línea %d está cancelada".formatted(lineId));
        }
        if (line.getStatus() == LineStatus.STOCKED_PAID || line.getStatus() == LineStatus.STOCKED_REFUNDED) {
            throw new BusinessRuleException("La línea %d se ha pasado a stock".formatted(lineId));
        }
        if (line.getStatus() != LineStatus.RECEIVED) {
            throw new BusinessRuleException("La línea %d todavía no se ha recibido".formatted(lineId));
        }
        line.markDelivered();
        return detailsOf(order);
    }

    // Entrega de golpe todas las camisetas recibidas del pedido (las que aún no han llegado se quedan como están)
    @Transactional
    public SalesOrderView.Details deliverAll(Long id) {
        SalesOrder order = findOpen(id);
        List<SalesOrderLine> received = order.getLines().stream()
                .filter(line -> line.getStatus() == LineStatus.RECEIVED)
                .toList();
        if (received.isEmpty()) {
            throw new BusinessRuleException("El pedido %s no tiene camisetas recibidas pendientes de entregar"
                    .formatted(order.getOrderNumber()));
        }
        received.forEach(SalesOrderLine::markDelivered);
        return detailsOf(order);
    }

    private static SalesOrderLine findLine(SalesOrder order, Long lineId) {
        return order.findLine(lineId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe la línea %d en el pedido %s"
                        .formatted(lineId, order.getOrderNumber())));
    }

    private static SalesOrderLine findPendingLine(SalesOrder order, Long lineId) {
        SalesOrderLine line = findLine(order, lineId);
        if (!line.isPending()) {
            throw new BusinessRuleException("La línea %d ya no está pendiente y no se puede modificar"
                    .formatted(lineId));
        }
        return line;
    }

    private SalesOrderView.Details detailsOf(SalesOrder order) {
        return SalesOrderView.details(order, customerOf(order.getCustomerId()));
    }

    // Sin cliente (pedido para uno mismo) no hay nada que buscar
    private CustomerSummary customerOf(Long customerId) {
        return customerId == null ? null : customerApi.getCustomer(customerId);
    }

    private static String customerNameOf(Map<Long, CustomerSummary> customers, Long customerId) {
        CustomerSummary customer = customers.get(customerId);
        return customer == null ? null : customer.name();
    }

    SalesOrder find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el pedido con id %d".formatted(id)));
    }

    SalesOrderLine buildLine(OrderLineDraft draft) {
        return buildLine(draft, null);
    }

    // Copia nombre y precios del catálogo; un precio que llega en la petición (incluido 0) tiene prioridad.
    // Al editar una línea, el producto y las personalizaciones que ya tenía se aceptan aunque se hayan
    // desactivado después: si no, no se podría ni cambiar la talla de esa camiseta.
    SalesOrderLine buildLine(OrderLineDraft draft, SalesOrderLine current) {
        boolean keepsProduct = current != null && current.getProductId().equals(draft.productId());
        CatalogItemSnapshot product = keepsProduct
                ? catalogApi.getProduct(draft.productId())
                : catalogApi.getActiveProduct(draft.productId());
        Set<Long> currentCustomizationIds = current == null
                ? Set.of()
                : current.getCustomizations().stream()
                        .map(LineCustomization::getCustomizationId)
                        .collect(Collectors.toSet());
        SalesOrderLine line = new SalesOrderLine(product.id(), product.name(), draft.kitName(), draft.size(),
                priceOrDefault(draft.unitSalePrice(), product.salePrice()),
                priceOrDefault(draft.unitPurchaseCost(), product.purchaseCost()));
        for (OrderLineDraft.Customization requested : draft.customizations()) {
            CatalogItemSnapshot customization = currentCustomizationIds.contains(requested.customizationId())
                    ? catalogApi.getCustomization(requested.customizationId())
                    : catalogApi.getActiveCustomization(requested.customizationId());
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
