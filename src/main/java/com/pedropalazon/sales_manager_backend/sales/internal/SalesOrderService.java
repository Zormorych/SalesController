package com.pedropalazon.sales_manager_backend.sales.internal;

import com.pedropalazon.sales_manager_backend.catalog.CatalogApi;
import com.pedropalazon.sales_manager_backend.catalog.CatalogItemSnapshot;
import com.pedropalazon.sales_manager_backend.customers.CustomerApi;
import com.pedropalazon.sales_manager_backend.customers.CustomerSummary;
import com.pedropalazon.sales_manager_backend.shared.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
        SalesOrder order = find(id);
        return SalesOrderView.details(order, customerApi.getCustomer(order.getCustomerId()));
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
