package com.pedropalazon.sales_manager_backend.customers.internal.web;

import com.pedropalazon.sales_manager_backend.customers.internal.Customer;
import com.pedropalazon.sales_manager_backend.customers.internal.CustomerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/customers")
@Tag(name = "Clientes", description = "Clientes con su teléfono de WhatsApp y notas")
class CustomerController {

    private final CustomerService service;

    CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    List<CustomerResponse> list(@RequestParam(required = false) String search) {
        return service.list(search).stream().map(CustomerResponse::from).toList();
    }

    @GetMapping("/{id}")
    CustomerResponse get(@PathVariable Long id) {
        return CustomerResponse.from(service.get(id));
    }

    @PostMapping
    ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        Customer customer = service.create(request.name(), request.phone(), request.notes());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(customer.getId())
                .toUri();
        return ResponseEntity.created(location).body(CustomerResponse.from(customer));
    }

    @PutMapping("/{id}")
    CustomerResponse update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return CustomerResponse.from(service.update(id, request.name(), request.phone(), request.notes()));
    }
}
