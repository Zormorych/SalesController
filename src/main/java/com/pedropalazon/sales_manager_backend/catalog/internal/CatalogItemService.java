package com.pedropalazon.sales_manager_backend.catalog.internal;

import com.pedropalazon.sales_manager_backend.shared.BusinessRuleException;
import com.pedropalazon.sales_manager_backend.shared.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

// Lógica común de productos y personalizaciones: listar, crear, editar y activar/desactivar.
public abstract class CatalogItemService<T extends CatalogItem> {

    private final CatalogItemRepository<T> repository;
    private final String notFoundMessage;
    private final String duplicateNameMessage;

    protected CatalogItemService(CatalogItemRepository<T> repository, String notFoundMessage,
            String duplicateNameMessage) {
        this.repository = repository;
        this.notFoundMessage = notFoundMessage;
        this.duplicateNameMessage = duplicateNameMessage;
    }

    protected abstract T newItem(String name, BigDecimal salePrice, BigDecimal purchaseCost);

    @Transactional(readOnly = true)
    public List<T> list(boolean includeInactive) {
        return includeInactive
                ? repository.findAllByOrderByNameAsc()
                : repository.findAllByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public T get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(notFoundMessage.formatted(id)));
    }

    @Transactional
    public T create(String name, BigDecimal salePrice, BigDecimal purchaseCost) {
        ensureNameIsFree(name, null);
        return repository.save(newItem(name, salePrice, purchaseCost));
    }

    @Transactional
    public T update(Long id, String name, BigDecimal salePrice, BigDecimal purchaseCost) {
        T item = get(id);
        ensureNameIsFree(name, id);
        item.update(name, salePrice, purchaseCost);
        return item;
    }

    @Transactional
    public T activate(Long id) {
        T item = get(id);
        item.activate();
        return item;
    }

    @Transactional
    public T deactivate(Long id) {
        T item = get(id);
        item.deactivate();
        return item;
    }

    private void ensureNameIsFree(String name, Long currentId) {
        String trimmedName = name.trim();
        boolean taken = currentId == null
                ? repository.existsByNameIgnoreCase(trimmedName)
                : repository.existsByNameIgnoreCaseAndIdNot(trimmedName, currentId);
        if (taken) {
            throw new BusinessRuleException(duplicateNameMessage.formatted(trimmedName));
        }
    }
}
