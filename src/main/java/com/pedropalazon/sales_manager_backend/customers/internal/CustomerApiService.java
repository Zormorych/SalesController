package com.pedropalazon.sales_manager_backend.customers.internal;

import com.pedropalazon.sales_manager_backend.customers.CustomerApi;
import com.pedropalazon.sales_manager_backend.customers.CustomerSummary;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CustomerApiService implements CustomerApi {

    private final CustomerService customerService;
    private final CustomerRepository customerRepository;

    CustomerApiService(CustomerService customerService, CustomerRepository customerRepository) {
        this.customerService = customerService;
        this.customerRepository = customerRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerSummary getCustomer(Long customerId) {
        return summaryOf(customerService.get(customerId));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, CustomerSummary> findCustomers(Collection<Long> customerIds) {
        // Los pedidos sin cliente aportan ids nulos: se ignoran
        return customerRepository.findAllById(customerIds.stream().filter(Objects::nonNull).toList()).stream()
                .map(CustomerApiService::summaryOf)
                .collect(Collectors.toMap(CustomerSummary::id, Function.identity()));
    }

    private static CustomerSummary summaryOf(Customer customer) {
        return new CustomerSummary(customer.getId(), customer.getName(), customer.getPhone());
    }
}
