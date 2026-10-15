package com.pedropalazon.sales_manager_backend.customers;

import java.util.Collection;
import java.util.Map;

// API pública de clientes para otros módulos (por ejemplo, ventas)
public interface CustomerApi {

    // Lanza ResourceNotFoundException si no existe
    CustomerSummary getCustomer(Long customerId);

    // Devuelve los clientes encontrados por id; los ids que no existen se omiten
    Map<Long, CustomerSummary> findCustomers(Collection<Long> customerIds);
}
