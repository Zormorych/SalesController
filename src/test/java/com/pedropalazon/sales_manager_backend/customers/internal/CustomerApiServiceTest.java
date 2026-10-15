package com.pedropalazon.sales_manager_backend.customers.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pedropalazon.sales_manager_backend.customers.CustomerApi;
import com.pedropalazon.sales_manager_backend.customers.CustomerSummary;
import com.pedropalazon.sales_manager_backend.shared.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CustomerApiServiceTest {

    @Autowired
    private CustomerApi customerApi;

    @Autowired
    private CustomerService customerService;

    @Test
    void givenExistingCustomer_whenRequested_thenReturnsSummary() {
        Customer customer = customerService.create("Ana Pérez", "600 333 444", "talla M");

        CustomerSummary summary = customerApi.getCustomer(customer.getId());

        assertThat(summary).isEqualTo(new CustomerSummary(customer.getId(), "Ana Pérez", "600333444"));
    }

    @Test
    void givenUnknownCustomer_whenRequested_thenThrowsNotFound() {
        assertThatThrownBy(() -> customerApi.getCustomer(999999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No existe el cliente con id 999999");
    }

    @Test
    void givenSeveralIds_whenFindingCustomers_thenReturnsExistingOnesById() {
        Customer ana = customerService.create("Ana Pérez", "600333444", null);
        Customer juan = customerService.create("Juan García", null, null);

        Map<Long, CustomerSummary> found = customerApi.findCustomers(List.of(ana.getId(), juan.getId(), 999999L));

        assertThat(found).containsOnlyKeys(ana.getId(), juan.getId());
        assertThat(found.get(juan.getId()).name()).isEqualTo("Juan García");
    }
}
