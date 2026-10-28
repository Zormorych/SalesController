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

    protected abstract T newItem(String name, BigDecimal salePrice, BigDecimal purchaseCost, String description);

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

    // Sin descripción (la usan sobre todo los tests)
    @Transactional
    public T create(String name, BigDecimal salePrice, BigDecimal purchaseCost) {
        return create(name, salePrice, purchaseCost, null);
    }

    @Transactional
    public T create(String name, BigDecimal salePrice, BigDecimal purchaseCost, String description) {
        ensureNameIsFree(name, null);
        return repository.save(newItem(name, salePrice, purchaseCost, description));
    }

    @Transactional
    public T update(Long id, String name, BigDecimal salePrice, BigDecimal purchaseCost, String description) {
        T item = get(id);
        ensureNameIsFree(name, id);
        item.update(name, salePrice, purchaseCost, description);
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
        repository.findFirstByNameIgnoreCase(trimmedName)
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    String message = duplicateNameMessage.formatted(trimmedName);
                    // Un artículo desactivado no sale en el listado por defecto: hay que decir por qué choca
                    throw new BusinessRuleException(existing.isActive()
                            ? message
                            : message + " (está desactivado: puedes reactivarlo)");
                });
    }
}
