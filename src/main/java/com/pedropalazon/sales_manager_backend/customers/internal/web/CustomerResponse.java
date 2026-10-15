package com.pedropalazon.sales_manager_backend.customers.internal.web;

import com.pedropalazon.sales_manager_backend.customers.internal.Customer;

public record CustomerResponse(Long id, String name, String phone, String notes) {

    static CustomerResponse from(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getName(), customer.getPhone(), customer.getNotes());
    }
}
