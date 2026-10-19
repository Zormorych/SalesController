package com.pedropalazon.sales_manager_backend.resale.internal;

import com.pedropalazon.sales_manager_backend.shared.BusinessRuleException;
import com.pedropalazon.sales_manager_backend.shared.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResaleItemService {

    private final ResaleItemRepository repository;

    public ResaleItemService(ResaleItemRepository repository) {
        this.repository = repository;
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

    private ResaleItem find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe la prenda con id %d".formatted(id)));
    }
}
