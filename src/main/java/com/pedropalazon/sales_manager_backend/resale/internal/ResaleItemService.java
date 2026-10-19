package com.pedropalazon.sales_manager_backend.resale.internal;

import com.pedropalazon.sales_manager_backend.purchasing.PurchasingApi;
import com.pedropalazon.sales_manager_backend.sales.OrderLineSnapshot;
import com.pedropalazon.sales_manager_backend.sales.SalesOrderApi;
import com.pedropalazon.sales_manager_backend.shared.BusinessRuleException;
import com.pedropalazon.sales_manager_backend.shared.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResaleItemService {

    private final ResaleItemRepository repository;
    private final SalesOrderApi salesOrderApi;
    private final PurchasingApi purchasingApi;

    public ResaleItemService(ResaleItemRepository repository, SalesOrderApi salesOrderApi, PurchasingApi purchasingApi) {
        this.repository = repository;
        this.salesOrderApi = salesOrderApi;
        this.purchasingApi = purchasingApi;
    }

    @Transactional(readOnly = true)
    public List<ResaleItemView> list(ResaleItemStatus status) {
        List<ResaleItem> items = status == null
                ? repository.findAllByOrderByIdDesc()
                : repository.findByStatusOrderByIdDesc(status);
        return items.stream().map(ResaleItemView::of).toList();
    }

    @Transactional(readOnly = true)
    public ResaleItemView get(Long id) {
        return ResaleItemView.of(find(id));
    }

    @Transactional
    public ResaleItemView create(ResaleItemDraft draft) {
        return ResaleItemView.of(repository.save(new ResaleItem(draft)));
    }

    // Se puede editar en cualquier estado para corregir errores; no toca el estado ni los datos de venta
    @Transactional
    public ResaleItemView update(Long id, ResaleItemDraft draft) {
        ResaleItem item = find(id);
        item.update(draft);
        return ResaleItemView.of(item);
    }

    @Transactional
    public ResaleItemView sell(Long id, BigDecimal salePrice, LocalDate saleDate) {
        ResaleItem item = find(id);
        if (item.isSold()) {
            throw new BusinessRuleException("La prenda %d ya está vendida".formatted(id));
        }
        item.sell(salePrice, saleDate);
        return ResaleItemView.of(item);
    }

    // Para corregir una venta apuntada por error
    @Transactional
    public ResaleItemView unsell(Long id) {
        ResaleItem item = find(id);
        if (!item.isSold()) {
            throw new BusinessRuleException("La prenda %d no está vendida".formatted(id));
        }
        item.unsell();
        return ResaleItemView.of(item);
    }

    // Una camiseta recibida que el cliente ya no quiere pasa a stock. Si la paga igualmente, su coste ya está en
    // la venta del pedido y la prenda entra a 0; si se le devuelve el dinero, entra con su coste más su envío
    @Transactional
    public ResaleItemView moveFromOrderLine(Long lineId, boolean customerPays, BigDecimal askingPrice) {
        OrderLineSnapshot line = salesOrderApi.moveLineToStock(lineId, customerPays);
        BigDecimal cost = customerPays
                ? BigDecimal.ZERO
                : line.totalCost().add(purchasingApi.shippingShareOf(lineId));
        ResaleItem item = ResaleItem.fromOrderLine(lineId, nameOf(line), line.size().getLabel(), cost, askingPrice);
        return ResaleItemView.of(repository.save(item));
    }

    // "Camiseta Player · Selección Española blanca · WILLIAMS JR 17"; una personalización sin texto va por su nombre.
    // Si no cabe, se recorta (luego se puede editar)
    private static String nameOf(OrderLineSnapshot line) {
        String name = line.productName() + " · " + line.kitName();
        if (!line.customizations().isEmpty()) {
            name += " · " + line.customizations().stream()
                    .map(customization -> customization.customText() == null || customization.customText().isBlank()
                            ? customization.name()
                            : customization.customText())
                    .collect(Collectors.joining(", "));
        }
        return name.length() <= ResaleItem.NAME_MAX_LENGTH
                ? name
                : name.substring(0, ResaleItem.NAME_MAX_LENGTH - 1) + "…";
    }

    private ResaleItem find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe la prenda con id %d".formatted(id)));
    }
}
