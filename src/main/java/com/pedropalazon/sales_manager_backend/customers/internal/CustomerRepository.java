package com.pedropalazon.sales_manager_backend.customers.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findAllByOrderByNameAsc();

    Optional<Customer> findByPhone(String phone);

    // Si el texto no tiene dígitos (digits = ''), no se compara el teléfono: si no, coincidiría con todos
    @Query("""
            select c from Customer c
            where lower(c.name) like lower(concat('%', :text, '%'))
               or (:digits <> '' and c.phone like concat('%', :digits, '%'))
            order by c.name""")
    List<Customer> search(@Param("text") String text, @Param("digits") String digits);
}
