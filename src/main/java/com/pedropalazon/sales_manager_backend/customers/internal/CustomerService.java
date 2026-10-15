package com.pedropalazon.sales_manager_backend.customers.internal;

import com.pedropalazon.sales_manager_backend.shared.BusinessRuleException;
import com.pedropalazon.sales_manager_backend.shared.ResourceNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Customer> list(String search) {
        if (search == null || search.isBlank()) {
            return repository.findAllByOrderByNameAsc();
        }
        String text = search.trim();
        return repository.search(text, text.replaceAll("\\D", ""));
    }

    @Transactional(readOnly = true)
    public Customer get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el cliente con id %d".formatted(id)));
    }

    @Transactional
    public Customer create(String name, String phone, String notes) {
        ensurePhoneIsFree(phone, null);
        return repository.save(new Customer(name, phone, notes));
    }

    @Transactional
    public Customer update(Long id, String name, String phone, String notes) {
        Customer customer = get(id);
        ensurePhoneIsFree(phone, id);
        customer.update(name, phone, notes);
        return customer;
    }

    private void ensurePhoneIsFree(String phone, Long currentId) {
        String normalizedPhone = Customer.normalizePhone(phone);
        if (normalizedPhone == null) {
            return;
        }
        repository.findByPhone(normalizedPhone)
                .filter(owner -> !owner.getId().equals(currentId))
                .ifPresent(owner -> {
                    throw new BusinessRuleException("El teléfono %s ya pertenece al cliente %s"
                            .formatted(normalizedPhone, owner.getName()));
                });
    }
}
